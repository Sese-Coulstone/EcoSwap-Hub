#!/bin/bash

echo "Creating EcoSwap-Hub microservices project structure..."

# Create root directories
mkdir -p ecoswap-platform ecoswap-services ecoswap-shared ecoswap-infra

# Infrastructure
mkdir -p ecoswap-infra/docker-compose
mkdir -p ecoswap-infra/monitoring

echo "Project structure created successfully!"
echo ""
echo "Next steps:"
echo "1. Run: chmod +x scripts/setup-directories.sh"
echo "2. Run: ./scripts/setup-directories.sh"
echo "3. Initialize each module with their respective pom.xml files"