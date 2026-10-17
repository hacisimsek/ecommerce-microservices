#!/bin/sh
# For each name in POSTGRES_MULTIPLE_DATABASES (e.g. "order_db,payment_db") creates a
# dedicated login role "<name without _db>_svc" that owns the database, so every service
# can only reach its own data. The role password is read from "<NAME>_PASSWORD"
# (e.g. ORDER_DB_PASSWORD). Runs once, from /docker-entrypoint-initdb.d, on an empty volume.
set -e

create_database() {
    database=$1
    role="${database%_db}_svc"
    password_var="$(echo "$database" | tr '[:lower:]' '[:upper:]')_PASSWORD"
    eval "password=\${$password_var:-}"
    if [ -z "$password" ]; then
        echo "Missing $password_var for database '$database'" >&2
        exit 1
    fi

    echo "Creating database '$database' owned by role '$role'"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" \
         -v role="$role" -v password="$password" -v database="$database" <<-'EOSQL'
        CREATE ROLE :"role" LOGIN PASSWORD :'password';
        CREATE DATABASE :"database" OWNER :"role";
        REVOKE ALL ON DATABASE :"database" FROM PUBLIC;
EOSQL
}

if [ -n "$POSTGRES_MULTIPLE_DATABASES" ]; then
    for db in $(echo "$POSTGRES_MULTIPLE_DATABASES" | tr ',' ' '); do
        create_database "$db"
    done
fi
