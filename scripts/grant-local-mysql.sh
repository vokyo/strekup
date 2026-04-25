#!/usr/bin/env bash
set -euo pipefail

mysql -u root -p < "$(dirname "$0")/grant-local-mysql.sql"
