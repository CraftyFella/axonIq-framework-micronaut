#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    -- Grant all privileges on database to postgres user
    GRANT ALL PRIVILEGES ON DATABASE axon_eventstore TO postgres;

    -- Create schema if it doesn't exist (optional)
    CREATE SCHEMA IF NOT EXISTS public;

    -- Enable extensions that might be useful
    CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
EOSQL

echo "PostgreSQL initialization complete!"