{smcl}
{* 2025-01-10}{...}
{cmd:help checkshp} 

{title:Title}

{phang}
{bf:checkshp} {hline 2} Geometric validation, cleaning, reprojection, and spatial intersection statistical analysis of Shapefiles

{title:Syntax}

{p 8 17 2}
{cmd:checkshp} {it:shpfile} [{cmd:,} {it:options}]

{synoptset 20 tabbed}{...}
{synopthdr:options}
{synoptline}
{synopt :{opt d:etail}}Detailed output mode, containing detailed information for each invalid feature{p_end}
{synopt :{opt s:ummary}}Summary output mode, showing only statistical information (default){p_end}
{synopt :{opt c:lean}}Remove invalid geometric features and save to a new file{p_end}
{synopt :{opt repro:ject(crs_or_tif)}}Reproject shapefile to specified coordinate system{p_end}
{synopt :{opt inters:ect(shpfile2)}}Calculate spatial intersection statistics with another shapefile{p_end}
{synopt :{opt merge}}In intersect mode, merge all features of shp2 before calculation{p_end}
{synopt :{opt group(string)}}In intersect mode, group statistics by specified field{p_end}
{synoptline}

{p 4 6 2}
Note: Options {cmd:detail} and {cmd:summary} are mutually exclusive; {cmd:intersect} is mutually exclusive with {cmd:reproject} (because intersect mode automatically aligns both shapefiles to the same appropriate projected coordinate system for area calculation), and {cmd:intersect} cannot be used with {cmd:detail} or {cmd:summary}.


{title:Description}

{pstd}
{cmd:checkshp} is a Stata command developed based on Java tools (using GeoTools and JTS) for geometric validation, cleaning, reprojection, and spatial intersection statistical analysis of Shapefiles.

{pstd}
The command supports three main modes:

{p2colset 9 29 31 2}{...}
{p2col :{bf:1. Check mode (default)}}Validate geometric validity of shapefile and detect invalid geometric features{p_end}
{p2col :{bf:2. Reproject mode}}Reproject shapefile to specified coordinate system{p_end}
{p2col :{bf:3. Intersect mode}}Calculate spatial intersection statistics between two shapefiles{p_end}
{p2colreset}{...}


{title:Options}

{phang}
{opt detail} specifies detailed output mode. In this mode, a CSV file ({it:shpfile}_invalid_detail.csv) containing detailed information for each invalid feature is generated, including error types and coordinate locations.

{phang}
{opt summary} specifies summary output mode. In this mode, only statistical summary information ({it:shpfile}_invalid_summary.csv) is generated, including the number of invalid features and basic statistics. This is the default mode.

{phang}
{opt clean} When this option is specified, invalid geometric features are automatically removed and the cleaned shapefile is saved as a new file ({it:shpfile}_cleaned.shp), while preserving the original file.

{phang}
{opt reproject(crs_or_tif)} enables reprojection mode. The shapefile can be reprojected to:{p_end}
{phang2}• {bf:EPSG code}: For example, {cmd:reproject(EPSG:4326)} or {cmd:reproject(4326)} (EPSG: prefix is automatically added).{p_end}
{phang2}• {bf:GeoTIFF file path}: For example, {cmd:reproject("C:/data/raster.tif")}, automatically reads the coordinate system from .tif/.tiff files.{p_end}
{phang}
Can be used with {cmd:clean} option to clean invalid features before reprojection. Output file will be saved as {it:shpfile}_reproj.shp.

{phang}
{opt intersect(shpfile2)} enables intersect mode. Calculates spatial intersection area and count statistics between the current shapefile and the specified shapefile. Output file is {it:shpfile}_intersect_{it:shpfile2}.csv.

{phang}
{opt merge} is used in intersect mode. All features of shp2 are merged first before participating in intersection calculation. This is useful for scenarios that require deduplication of shp2 features.

{phang}
{opt group(string)} is used in intersect mode. Groups statistics by the specified field of shp2. For example, {cmd:group("region_name")} generates grouped statistics.


{title:Examples}

{phang}
Check and clean invalid features:

{p 12 16 2}
{cmd:.checkshp "fujian.shp", detail clean}{break}

{phang}
Reproject shapefile to WGS84 (EPSG:4326):

{p 12 16 2}
{cmd:.checkshp "fujian.shp", reproject(EPSG:4326)}{break}

{phang}
Calculate intersection statistics grouped by a field:

{p 12 16 2}
{cmd:.checkshp "fujian.shp", intersect("fuzhou_building.shp") group("Floor")}{break}


{title:Requirements}

{pstd}
{bf:Runtime environment requirements:}{p_end}
{phang2}• Java 17 or higher (automatically detected from Stata's Java configuration or system PATH){p_end}
{phang2}• checkshp-0.1.0.jar file (JAR package needs to be built first; automatically searched in current directory, build/libs, or Stata's ado directory){p_end}
{phang2}• All auxiliary files of the Shapefile (.shx, .dbf, .prj, etc.) must be in the same directory as the main file{p_end}


{title:Technical Details}

{pstd}
The command automatically handles the following technical details:{p_end}
{phang2}• Automatic coordinate system detection and transformation (automatically identifies geographic coordinate systems and converts to appropriate UTM projected coordinate systems for area calculation){p_end}
{phang2}• Uses STRtree spatial index to accelerate large-scale data calculations{p_end}
{phang2}• Stream processing prevents memory overflow, suitable for processing millions of features{p_end}



{title:Author}

{pstd}
Chunxia Chen{p_end}
{pstd}
School of Management, Xiamen University{p_end}
{pstd}
Email: triciachen6754@126.com{p_end}
