{smcl}
{* 2025-01-10}{...}
{cmd:help intershp} 

{title:Title}

{phang}
{bf:intershp} {hline 2} Calculate spatial intersection statistics between two Shapefiles

{title:Syntax}

{p 8 17 2}
{cmd:intershp} {it:shpfile1} {cmd:with(}{it:shpfile2}{cmd:)} [{cmd:,} {it:options}]

{synoptset 20 tabbed}{...}
{synopthdr:options}
{synoptline}
{synopt :{opt merge}}Merge all features of shp2 before intersection calculation{p_end}
{synopt :{opt group(string)}}Group statistics by specified field of shp2{p_end}
{synoptline}

{p 4 6 2}
Note: {cmd:with()} is required and must be specified before the comma.


{title:Description}

{pstd}
{cmd:intershp} calculates spatial intersection statistics between two shapefiles. It computes the intersection area and count statistics for features in the first shapefile that intersect with features in the second shapefile specified in {cmd:WITH()}.

{pstd}
The command automatically aligns both shapefiles to the same appropriate projected coordinate system for accurate area calculation. Results are saved as a CSV file ({it:shpfile1}_intersection_stats.csv).

{pstd}
The {cmd:merge} option can be used to merge all features of the second shapefile before intersection calculation, which is useful for scenarios that require deduplication. The {cmd:group()} option allows grouping statistics by a specified field of the second shapefile.

{title:Examples}

{phang}
Calculate intersection statistics between two shapefiles:

{p 12 16 2}
{cmd:. intershp "fujian.shp" with("fuzhou_building.shp")}{break}

{phang}
Calculate intersection with merged shp2 features:

{p 12 16 2}
{cmd:. intershp "fujian.shp" with("fuzhou_building.shp"), merge}{break}

{phang}
Calculate intersection statistics grouped by a field:

{p 12 16 2}
{cmd:. intershp "fujian.shp" with("fuzhou_building.shp"), group("Floor")}{break}

{title:Requirements}

{pstd}
{bf:Runtime environment requirements:}{p_end}
{phang2}• Java 17 or higher (automatically detected from Stata's Java configuration or system PATH){p_end}
{phang2}• checkshp-0.1.0.jar file (JAR package needs to be built first; automatically searched in current directory, build/libs, or Stata's ado directory){p_end}
{phang2}• All auxiliary files of both Shapefiles (.shx, .dbf, .prj, etc.) must be in the same directory as the main file{p_end}


{title:Technical Details}

{pstd}
The command uses JTS (Java Topology Suite) and GeoTools libraries for spatial intersection calculations. It automatically handles coordinate system alignment by projecting both shapefiles to an appropriate projected coordinate system (typically UTM) for accurate area calculations.

{pstd}
The command uses STRtree spatial index to accelerate large-scale intersection calculations, making it efficient for processing large datasets with millions of features. Stream processing prevents memory overflow issues.


{title:Author}

{pstd}
Chunxia Chen{p_end}
{pstd}
School of Management, Xiamen University{p_end}
{pstd}
Email: triciachen6754@126.com{p_end}

{title:Also see}

{psee}
Online:  {help checkshp}, {help reprojshp}, {help areashp}
{p_end}
