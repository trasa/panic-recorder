#!/bin/bash
docker compose pull app
docker compose up -d app
docker compose ps

