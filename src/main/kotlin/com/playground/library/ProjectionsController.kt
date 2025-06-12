package com.playground.library

import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.serde.annotation.Serdeable
import org.axonframework.config.Configuration
import org.axonframework.eventhandling.StreamingEventProcessor
import org.axonframework.eventhandling.TrackingEventProcessor
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
                ProjectionInfo(
                    name = name,
                    type = processor.javaClass.simpleName,
                    status = if (processor is TrackingEventProcessor) {
                        if (isReplaying(processor)) "REPLAYING" else "RUNNING"
                    } else {
                        "RUNNING"
                    },
                    supportsReplay = processor is StreamingEventProcessor
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

            // Start a replay and return success response
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
        val isReplaying = isReplaying(trackingProcessor)

        // Get progress information if available
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

    private fun isReplaying(processor: TrackingEventProcessor): Boolean {
        return processor.processingStatus().values
            .any { status -> status.isReplaying }
    }
}

@Serdeable
data class ProjectionInfo(
    val name: String,
    val type: String,
    val status: String,
    val supportsReplay: Boolean
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