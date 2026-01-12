# checkshp

A Java tool based on GeoTools and JTS for geometric validation, cleaning, reprojection, and spatial intersection statistical analysis of Shapefiles.

## Installation

```stata
net install checkshp, from("https://raw.githubusercontent.com/tricia1353/checkshp/main/") replace
```
 
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

### 3. Polygon Area Calculation Mode (Area Mode)
- **Area Calculation**: Calculates the area of all polygon and multipolygon features in a Shapefile
- **Automatic Coordinate System Conversion**:
  - **Critical**: Area calculation requires a projected coordinate system for accuracy
  - Automatically detects geographic coordinate systems (e.g., WGS84) and converts to appropriate UTM projection based on the center of the Shapefile
  - If Shapefile uses a projected CRS, calculates areas directly in that coordinate system

### 4. Spatial Intersection Statistics Mode (Intersection Mode)
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

## Command Overview

The toolkit exposes four commands that follow the workflow described above:

- **Geometric validation**
  - `checkshp` validates geometric validity of Shapefiles, detects invalid features, and optionally removes invalid features to generate cleaned Shapefiles with `_clean` suffix
- **Reprojection**
  - `reprojshp` reprojects Shapefiles to a specified coordinate reference system (CRS) using EPSG codes, GeoTIFF files, or reference Shapefiles, and generates new files with `_reproj` suffix

- **Polygon area calculation**
  - `areashp` calculates the area of all polygon and multipolygon features in a Shapefile, automatically converting geographic coordinate systems to projected coordinate systems for accurate area calculation, and outputs results to a CSV file
  
- **Spatial intersection statistics**
  - `intershp` computes spatial intersection areas and counts between two Shapefiles, with support for merge mode and grouped statistics by specified fields

## Workflow Snapshot

The following workflow demonstrates how the commands work together:

1. Validate and clean Shapefiles with `checkshp` to detect and optionally remove invalid geometric features before spatial operations.
2. Reproject Shapefiles to a common coordinate system using `reprojshp` when necessary, using EPSG codes or reference files (GeoTIFF or Shapefile).
3. Calculate polygon areas with `areashp` to compute accurate area measurements for polygon features, with automatic coordinate system conversion for geographic CRS.
4. Compute spatial intersection statistics with `intershp` to analyze spatial relationships between Shapefiles, with support for merge mode and grouped statistics.


## Example

The following examples demonstrate common usage patterns:

- Validate and clean Shapefiles
  ```stata
  checkshp "fujian.shp", detail clean
  ```

- Reproject Shapefiles using different methods
  ```stata
  * Using EPSG code
  reprojshp "fujian.shp", crs(EPSG:4326)
  ```

- Calculate spatial intersection statistics
  ```stata
  * Basic intersection calculation
  intershp "fujian.shp" with("fuzhou_building.shp")
  
  * Intersection with grouped statistics
  intershp "fujian.shp" with("fuzhou_building.shp"), group(Floor)
  ```

- Calculate polygon areas
  ```stata
  * Calculate areas (output to default CSV file: fujian_area.csv)
  areashp "fujian.shp"
  ```

## Runtime Environment

- **JDK**: 17 or higher
- **Build Tool**: Gradle 8.5+ or Maven 3.9+
- **Dependencies**:
  - GeoTools 34.0
  - JTS 1.19.0
  - Commons IO, Commons Lang3, Guava
