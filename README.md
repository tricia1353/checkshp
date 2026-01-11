# gcheckshp

A Java tool based on GeoTools and JTS for geometric validation, cleaning, reprojection, and spatial intersection statistical analysis of Shapefiles.

## Features

### 1. Geometric Validation Mode (Check Mode)
- **Geometry Validation**: Detects null geometries, invalid geometries, and auto-fixable geometries
- **Detailed Reports**: Provides detailed error information and coordinate locations
- **Auto-cleaning**: Optionally removes invalid features and generates cleaned Shapefiles with `_clean` suffix
- **Statistics**: Outputs feature counts, geometry types, spatial extents, and other statistical information

### 2. Reprojection Mode (Reproject Mode)
- **Flexible Input Format**:
  - EPSG codes: `EPSG:4326` or numeric code `4326` (automatically adds EPSG: prefix)
  - GeoTIFF file path: Automatically reads coordinate system from .tif/.tiff files (uses `getCoordinateReferenceSystem()` method without reading complete image data)
  - **Output**: Generates new files with `_reproj` suffix while preserving original files

### 3. Spatial Intersection Statistics Mode (Intersection Mode)
- **Intersection Calculation**: Computes spatial intersection areas and counts between two Shapefiles
- **Smart Coordinate System Handling**:
  - Automatically detects geographic coordinate systems (e.g., WGS84) and converts to appropriate UTM projected coordinate systems for area calculation
  - Automatically handles coordinate transformations between different coordinate systems
  - Automatically determines area units (square meters, square degrees, etc.) based on coordinate system
  - UTM zone selection is based on the center point of both Shapefiles' boundaries
- **Flexible Calculation Modes**:
  - **Standard Mode** (default): Uses STRtree spatial index to compute intersections feature by feature, suitable for large-scale data
  - **Merge Mode** : Merges all features of shp2 before computing intersections, enabling deduplication of shp2 features
- **Grouped Statistics**: Supports grouped statistics by specified field of shp2
- **Performance Optimization**:
  - Uses STRtree spatial index (recommended node capacity: 100) to accelerate large-scale data calculations
  - Envelope pre-check: Checks bounding box intersections first to avoid unnecessary geometric calculations
  - Stream processing: Prevents memory overflow, suitable for processing millions of features

## Runtime Environment

- **JDK**: 17 or higher
- **Build Tool**: Gradle 8.5+ or Maven 3.9+
- **Dependencies**:
  - GeoTools 34.0
  - JTS 1.19.0
  - Commons IO, Commons Lang3, Guava