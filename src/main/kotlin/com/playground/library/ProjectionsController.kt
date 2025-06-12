package com.playground.library

import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.*
import io.micronaut.serde.annotation.Serdeable
import org.axonframework.config.Configuration
import org.axonframework.eventhandling.StreamingEventProcessor
import org.axonframework.eventhandling.TrackingEventProcessor
import reactor.core.publisher.Mono
import java.net.URI
import java.util.OptionalLong
import java.util.concurrent.CompletableFuture

@Controller("/admin/projections")
class ProjectionsController(private val configuration: Configuration) {

    @Get
    @Produces(MediaType.APPLICATION_JSON)
    fun listProjections(): HttpResponse<List<ProjectionInfo>> {
        val projections = configuration.eventProcessingConfiguration()
            .eventProcessors()
            .map { (name, processor) ->
                val segmentDetails = if (processor is TrackingEventProcessor) {
                    processor.processingStatus().map { (segmentId, status) ->
                        SegmentStatus(
                            segmentId = segmentId,
                            caughtUp = status.isCaughtUp,
                            replaying = status.isReplaying,
                            tokenPosition = status.currentPosition,
                            errorState = status.error?.message
                        )
                    }.toList()
                } else emptyList()
                ProjectionInfo(
                    name = name,
                    type = processor.javaClass.simpleName,
                    status = if (processor is TrackingEventProcessor) {
                        if (isProcessorReplaying(processor)) "REPLAYING" else "RUNNING"
                    } else {
                        "RUNNING"
                    },
                    supportsReplay = processor is StreamingEventProcessor,
                    segments = segmentDetails
                )
            }
            .sortedBy { it.name }

        return HttpResponse.ok(projections)
    }

    @Post("/{name}/replay")
    @Produces(MediaType.APPLICATION_JSON)
    fun replayProjection(name: String): HttpResponse<ReplayResponse> {
        val processor = configuration.eventProcessingConfiguration()
            .eventProcessor(name, StreamingEventProcessor::class.java)

        return if (processor.isPresent) {
            val streamingProcessor = processor.get()
            CompletableFuture.runAsync {
                streamingProcessor.shutDown()
                streamingProcessor.resetTokens()
                streamingProcessor.start()
            }
            val responseBody = ReplayResponse(
                name = name,
                status = "REPLAY_STARTED",
                message = "Replay started for projection: $name"
            )
            val statusUri = URI("/admin/projections/$name/status")
            HttpResponse.accepted<ReplayResponse>(statusUri).body(responseBody)
        } else {
            HttpResponse.badRequest(
                ReplayResponse(
                    name = name,
                    status = "ERROR",
                    message = "Projection not found or does not support replay"
                )
            )
        }
    }

    @Get("/{name}/status")
    @Produces(MediaType.APPLICATION_JSON)
    fun getReplayStatus(name: String): HttpResponse<ReplayStatusInfo> {
        val processor = configuration.eventProcessingConfiguration()
            .eventProcessor(name, TrackingEventProcessor::class.java)

        if (processor.isEmpty) {
            return HttpResponse.notFound()
        }

        val trackingProcessor = processor.get()
        val isReplaying = isProcessorReplaying(trackingProcessor)
        val segments = trackingProcessor.processingStatus()
        val segmentStatus = segments.map { (segmentId, status) ->
            SegmentStatus(
                segmentId = segmentId,
                caughtUp = status.isCaughtUp,
                replaying = status.isReplaying,
                tokenPosition = status.currentPosition,
                errorState = status.error?.message
            )
        }

        val replayStatus = ReplayStatusInfo(
            name = name,
            active = trackingProcessor.isRunning,
            isReplaying = isReplaying,
            segmentCount = segments.size,
            segments = segmentStatus
        )

        return HttpResponse.ok(replayStatus)
    }

    @Post("/{name}/scale")
    @Produces(MediaType.APPLICATION_JSON)
    fun splitOrMergeSegments(
        name: String,
        @Body scaleRequest: ScaleRequest
    ): HttpResponse<Mono<ScaleResultResponse>> {
        val processorOpt = configuration.eventProcessingConfiguration()
            .eventProcessor(name, TrackingEventProcessor::class.java)

        if (processorOpt.isEmpty) {
            return HttpResponse.notFound(
                Mono.just(
                    ScaleResultResponse(
                        name,
                        "ERROR",
                        "Processor not found or not a TrackingEventProcessor",
                        emptyList()
                    )
                )
            )
        }

        val processor = processorOpt.get()
        val currentSegments = processor.processingStatus().size
        val desiredSegments = scaleRequest.segmentCount

        if (desiredSegments == currentSegments && scaleRequest.threadCount <= 0) {
            return HttpResponse.ok(
                Mono.just(
                    ScaleResultResponse(
                        name,
                        "NO_CHANGE",
                        "No changes requested",
                        emptyList()
                    )
                )
            )
        }

        val mono = Mono.defer {
            val operations = mutableListOf<CompletableFuture<String>>()
            val results = mutableListOf<SegmentOperationResult>()

            // Scale segments
            if (desiredSegments > currentSegments) {
                // Split segments to increase count
                var remaining = desiredSegments - currentSegments
                var i = 0

                while (remaining > 0) {
                    val segmentIds = processor.processingStatus().keys.sorted()
                    if (segmentIds.isEmpty()) break

                    if (i >= segmentIds.size) {
                        i = 0 // Reset index if we need more splits than available segments
                    }

                    val segmentId = segmentIds[i]
                    val futureResult = processor.splitSegment(segmentId)
                        .thenApply {
                            val result = SegmentOperationResult(
                                operation = "SPLIT",
                                segmentId = segmentId,
                                success = true
                            )
                            results.add(result)
                            "Split segment $segmentId"
                        }
                        .exceptionally { ex ->
                            val result = SegmentOperationResult(
                                operation = "SPLIT",
                                segmentId = segmentId,
                                success = false,
                                errorMessage = ex.message
                            )
                            results.add(result)
                            "Failed to split segment $segmentId: ${ex.message}"
                        }

                    operations.add(futureResult)
                    remaining--
                    i++
                }
            } else if (desiredSegments < currentSegments) {
                // Merge segments to decrease count
                var toReduce = currentSegments - desiredSegments

                while (toReduce > 0) {
                    val segmentIds = processor.processingStatus().keys.sorted()
                    if (segmentIds.size <= 1) break // Can't merge if only one segment left

                    val highestSegment = segmentIds.last()
                    val futureResult = processor.mergeSegment(highestSegment)
                        .thenApply {
                            val result = SegmentOperationResult(
                                operation = "MERGE",
                                segmentId = highestSegment,
                                success = true
                            )
                            results.add(result)
                            "Merged segment $highestSegment"
                        }
                        .exceptionally { ex ->
                            val result = SegmentOperationResult(
                                operation = "MERGE",
                                segmentId = highestSegment,
                                success = false,
                                errorMessage = ex.message
                            )
                            results.add(result)
                            "Failed to merge segment $highestSegment: ${ex.message}"
                        }

                    operations.add(futureResult)
                    toReduce--
                }
            }

            // Handle thread count message (just informational)
            val threadCountMessage = if (scaleRequest.threadCount > 0) {
                " (thread count changes require restart)"
            } else {
                ""
            }

            // Create combined future and convert to Mono
            Mono.fromFuture(
                CompletableFuture.allOf(*operations.toTypedArray())
                    .thenApply {
                        ScaleResultResponse(
                            name = name,
                            status = "COMPLETED",
                            message = "Scaling processor to $desiredSegments segments$threadCountMessage",
                            operations = results
                        )
                    }
            )
        }
        val statusUri = URI("/admin/projections/$name/status")
        return HttpResponse.accepted<Mono<ScaleResultResponse>>(statusUri).body(mono)
    }

    private fun isProcessorReplaying(processor: TrackingEventProcessor): Boolean {
        return processor.processingStatus().values.any { status -> status.isReplaying }
    }
}

@Serdeable
data class SegmentOperationResult(
    val operation: String,
    val segmentId: Int,
    val success: Boolean,
    val errorMessage: String? = null
)

@Serdeable
data class ScaleResultResponse(
    val name: String,
    val status: String,
    val message: String,
    val operations: List<SegmentOperationResult>
)

@Serdeable
data class ProjectionInfo(
    val name: String,
    val type: String,
    val status: String,
    val supportsReplay: Boolean,
    val segments: List<SegmentStatus>
)

@Serdeable
data class ReplayResponse(
    val name: String,
    val status: String,
    val message: String
)

@Serdeable
data class ReplayStatusInfo(
    val name: String,
    val active: Boolean,
    val isReplaying: Boolean,
    val segmentCount: Int,
    val segments: List<SegmentStatus>
)

@Serdeable
data class SegmentStatus(
    val segmentId: Int,
    val caughtUp: Boolean,
    val replaying: Boolean,
    val tokenPosition: OptionalLong?,
    val errorState: String?
)

@Serdeable
data class ScaleRequest(
    val segmentCount: Int,
    val threadCount: Int
)

@Serdeable
data class ScaleResponse(
    val name: String,
    val status: String,
    val message: String
)