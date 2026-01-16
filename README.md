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
- **Flexible Coordinate System Handling**:
  - **Critical**: Area calculation requires a projected coordinate system for accuracy. Optional `crs()` option allows you to specify:
    - EPSG codes: `EPSG:3857`
    - Reference TIF file: path to a GeoTIFF file
    - Reference SHP file: path to a Shapefile


### 4. Spatial Intersection Statistics Mode (Intersection Mode)
- **Intersection Calculation**: Computes spatial intersection areas and counts between two Shapefiles
- **Coordinate Reference System** (Required):
  - The `crs()` option is required and supports multiple formats:
    - EPSG codes: `crs(EPSG:3857)` or `crs(3857)`
    - Reference TIF file: `crs(reference.tif)` - reads CRS from GeoTIFF file
    - Reference SHP file: `crs(reference.shp)` - reads CRS from Shapefile
  - Automatically handles coordinate transformations between different coordinate systems
  - Automatically determines area units (square meters, square degrees, etc.) based on coordinate system
  
- **Flexible Calculation Modes**:
  - **Standard Mode** (default): Uses STRtree spatial index to compute intersections feature by feature.
  - **Merge Mode**: Detects and merges overlapping features of shp2 before computing intersections, which enables efficient deduplication.
- **Grouped Statistics**: Supports grouped statistics by specified field of shp2
- **Performance Optimization**:
  - Uses STRtree spatial index (recommended node capacity: 100) to accelerate large-scale data calculations
  - Envelope pre-check: Checks bounding box intersections first to avoid unnecessary geometric calculations
  - Stream processing: Prevents memory overflow, suitable for processing millions of features
  - Clip option: Reduces data range before processing, especially useful when dealing with global data

## Command Overview

The toolkit exposes four commands that follow the workflow described above:

- **Geometric validation**
  - `checkshp` validates geometric validity of Shapefiles, detects invalid features, and optionally removes invalid features to generate cleaned Shapefiles with `_clean` suffix
- **Reprojection**
  - `reprojshp` reprojects Shapefiles to a specified coordinate reference system (CRS) using EPSG codes, GeoTIFF files, or reference Shapefiles, and generates new files with `_reproj` suffix

- **Polygon area calculation**
  - `areashp` calculates the area of all polygon and multipolygon features in a Shapefile and outputs results to a CSV file. 
  
- **Spatial intersection statistics**
  - `intershp` computes spatial intersection areas and counts between two Shapefiles. The `crs()` option is required. Supports merge mode (deduplication of overlapping features), automatic clipping, and grouped statistics by specified fields.

## Workflow Snapshot

The following workflow demonstrates how the commands work together:

1. Validate and clean Shapefiles with `checkshp` to detect and optionally remove invalid geometric features before spatial operations.
2. Reproject Shapefiles to a common coordinate system using `reprojshp` when necessary, using EPSG codes or reference files (GeoTIFF or Shapefile).
3. Calculate polygon areas with `areashp` to compute accurate area measurements for polygon features. Optionally specify projection using `crs()` option.
4. Compute spatial intersection statistics with `intershp` to analyze spatial relationships between Shapefiles. The `crs()` option is required. Supports merge mode (deduplication of overlapping features), automatic clipping, and grouped statistics.


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
  * Basic intersection calculation (crs is required)
  intershp "fujian.shp" with("fuzhou_building.shp"), crs(EPSG:3857)
    
  * Intersection with grouped statistics
  intershp "fujian.shp" with("fuzhou_building.shp"), crs(EPSG:3857) group(Floor)
  
  ```

- Calculate polygon areas
  ```stata
  * Calculate areas with specified projection
  areashp "fujian.shp", projection(EPSG:3857)
  
  ```

## Runtime Environment

- **JDK**: 17 or higher
- **Build Tool**: Gradle 8.5+ or Maven 3.9+
- **Dependencies**:
  - GeoTools 34.0
  - JTS 1.19.0
  - Commons IO, Commons Lang3, Guava
