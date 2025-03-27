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

## Database Schema

The tool creates the following tables in PostgreSQL:

### class_details
Information about Java classes and files
```sql
CREATE TABLE IF NOT EXISTS class_details (
    id BIGSERIAL PRIMARY KEY,
    package_name VARCHAR(512) NOT NULL,
    class_name VARCHAR(512) NOT NULL,
    file_name VARCHAR(512) NOT NULL,
    file_path TEXT NOT NULL,
    loc INTEGER NOT NULL,
    comment_count INTEGER NOT NULL,
    method_count INTEGER NOT NULL,
    class_count INTEGER NOT NULL,
    comment_density NUMERIC NOT NULL,
    import_count INTEGER NOT NULL,
    imports TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### class_import_details
Import statements used in each class
```sql
CREATE TABLE IF NOT EXISTS class_import_details (
    id BIGSERIAL PRIMARY KEY,
    class_details_id BIGINT NOT NULL,
    package_name VARCHAR(512) NOT NULL,
    class_name VARCHAR(512) NOT NULL,
    file_name VARCHAR(512) NOT NULL,
    file_path TEXT NOT NULL,
    import_name TEXT NOT NULL,
    is_static BOOLEAN NOT NULL,
    is_asterisk BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (class_details_id) REFERENCES class_details(id)
);
```

### class_field_details
Fields/variables declared in each class
```sql
CREATE TABLE IF NOT EXISTS class_field_details (
    id BIGSERIAL PRIMARY KEY,
    class_details_id BIGINT NOT NULL,
    package_name VARCHAR(512) NOT NULL,
    class_name VARCHAR(512) NOT NULL,
    file_name VARCHAR(512) NOT NULL,
    file_path TEXT NOT NULL,
    field_name VARCHAR(512) NOT NULL,
    type VARCHAR(512) NOT NULL,
    is_static BOOLEAN NOT NULL,
    is_final BOOLEAN NOT NULL,
    access_modifier VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (class_details_id) REFERENCES class_details(id)
);
```

### method_details
Information about class methods
```sql
CREATE TABLE IF NOT EXISTS method_details (
    id BIGSERIAL PRIMARY KEY,
    class_details_id BIGINT NOT NULL,
    package_name VARCHAR(512) NOT NULL,
    class_name VARCHAR(512) NOT NULL,
    file_name VARCHAR(512) NOT NULL,
    file_path TEXT NOT NULL,
    method_name VARCHAR(512) NOT NULL,
    line_count INTEGER NOT NULL,
    parameter_count INTEGER NOT NULL,
    complexity_score INTEGER NOT NULL,
    return_type VARCHAR(512) NOT NULL,
    access_modifier VARCHAR(150) NOT NULL,
    is_static BOOLEAN NOT NULL,
    parameter_details TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (class_details_id) REFERENCES class_details(id)
);
```

### method_parameter_details
Parameters of each method
```sql
CREATE TABLE IF NOT EXISTS method_parameter_details (
    id BIGSERIAL PRIMARY KEY,
    method_details_id BIGINT NOT NULL,
    class_details_id BIGINT NOT NULL,
    package_name VARCHAR(512) NOT NULL,
    class_name VARCHAR(512) NOT NULL,
    file_name VARCHAR(512) NOT NULL,
    file_path TEXT NOT NULL,
    method_name VARCHAR(512) NOT NULL,
    parameter_name VARCHAR(512) NOT NULL,
    parameter_type VARCHAR(512) NOT NULL,
    parameter_index INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (method_details_id) REFERENCES method_details(id),
    FOREIGN KEY (class_details_id) REFERENCES class_details(id)
);
```

### endpoint_details
REST API endpoints detected in the code
```sql
CREATE TABLE IF NOT EXISTS endpoint_details (
    id BIGSERIAL PRIMARY KEY,
    class_details_id BIGINT NOT NULL,
    method_details_id BIGINT NOT NULL,
    package_name VARCHAR(512),
    class_name VARCHAR(512) NOT NULL,
    method_name VARCHAR(512) NOT NULL,
    file_name VARCHAR(512) NOT NULL,
    file_path TEXT NOT NULL,
    http_method VARCHAR(45) NOT NULL,
    endpoint TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (class_details_id) REFERENCES class_details(id),
    FOREIGN KEY (method_details_id) REFERENCES method_details(id)
);
```

## Future Enhancements

The following features are planned for future releases:

- **Web Interface**: Develop a browser-based dashboard for exploring and visualizing analysis results
- **Documentation Export**: Support exporting analysis results to various formats including HTML, PDF, and Markdown
- **CI/CD Integration**: Create plugins for popular CI/CD platforms to automatically update documentation when code changes
- **Visual Diagrams**: Generate interactive diagrams showing class relationships and call hierarchies
- **Multi-language Support**: Extend analysis capabilities to support additional programming languages beyond Java
