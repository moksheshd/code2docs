# Java Code Analysis & Documentation Generator

A comprehensive tool for analyzing Java source code and automatically generating detailed documentation from its structure.

## Overview

This project provides a set of analyzers that extract detailed structural information from Java source code, storing it in a PostgreSQL database for further analysis and documentation generation. The tool is designed to help developers understand, document, and maintain complex Java codebases.

## Features

- **Source Code Analysis**: Extracts metrics and structural information from Java code
- **Multi-level Analysis**: Analyzes files, classes, methods, parameters, fields, and imports
- **API Endpoint Detection**: Identifies Spring Web API endpoints and their HTTP methods
- **Call Stack Mapping**: Maps method invocation hierarchies to visualize code flow
- **Database Storage**: Stores all analysis results in PostgreSQL for flexible querying
- **Comprehensive Metrics**: Tracks code complexity, comment density, line counts, and more

## Use Cases

- **Automated Documentation Generation**: Create comprehensive code documentation automatically
- **API Reference Creation**: Generate API documentation for REST services
- **Code Quality Assessment**: Identify complex or poorly documented sections of code
- **Codebase Visualization**: Generate diagrams of class relationships and call flows
- **Technical Debt Management**: Identify areas needing refactoring or better documentation

## Requirements

- Java 17 or higher
- PostgreSQL database
- JavaParser library
- SootUp library (for call stack analysis)
