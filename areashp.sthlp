{smcl}
{* 2025-01-10}{...}
{cmd:help areashp} 

{title:Title}

{phang}
{bf:areashp} {hline 2} Calculate polygon areas in Shapefiles

{title:Syntax}

{p 8 17 2}
{cmd:areashp} {it:shpfile} [{cmd:,} {it:options}]

{synoptset 20 tabbed}{...}
{synopthdr:options}
{synoptline}
{synopt :{opt s:ave(filename)}}Specify output CSV file path (default: {it:shpfile}_area.csv){p_end}
{synoptline}


{title:Description}

{pstd}
{cmd:areashp} calculates the area of all polygon and multipolygon features in a shapefile. The results are saved to a CSV file containing all original attribute fields plus an "Area" column.

{pstd}
{bf:Important:} Area calculation requires a projected coordinate system for accuracy. The command automatically handles coordinate reference systems (CRS):
{p_end}
{phang2}• If the shapefile uses a geographic CRS (e.g., WGS84), it {bf:automatically converts} to an appropriate UTM projection based on the center of the shapefile for accurate area calculation{p_end}
{phang2}• If the shapefile uses a projected CRS, it calculates areas directly in that coordinate system{p_end}
{phang2}• If the shapefile is missing CRS information, the command attempts to infer whether coordinates are geographic (based on coordinate ranges) and converts to UTM if needed{p_end}
{phang2}• The area unit is automatically determined from the CRS (typically square meters for projected systems){p_end}

{pstd}
{bf:Note:} Geographic coordinate systems (e.g., WGS84 with degrees) cannot provide accurate area measurements. The command will always convert to a projected coordinate system to ensure accurate area calculations.

{pstd}
The output CSV file contains all original attribute fields from the shapefile, plus an "Area" column with the calculated area for each polygon feature. Non-polygon features (points, lines) are excluded from the output.


{title:Options}

{phang}
{opt save(filename)} specifies the output CSV file path. If not specified, the output file will be named {it:shpfile}_area.csv in the same directory as the input shapefile.


{title:Examples}

{phang}
Calculate polygon areas (output to default CSV file):

{p 12 16 2}
{cmd:. areashp "fujian.shp"}{break}

{phang}
Calculate polygon areas and save to a specific file:

{p 12 16 2}
{cmd:. areashp "fujian.shp", save("fujian_areas.csv")}{break}


{title:Requirements}

{pstd}
{bf:Runtime environment requirements:}{p_end}
{phang2}• Java 17 or higher (automatically detected from Stata's Java configuration or system PATH){p_end}
{phang2}• checkshp-0.1.0.jar file (JAR package needs to be built first; automatically searched in current directory, build/libs, or Stata's ado directory){p_end}
{phang2}• All auxiliary files of the Shapefile (.shx, .dbf, .prj, etc.) must be in the same directory as the main file{p_end}


{title:Technical Details}

{pstd}
The command uses GeoTools and JTS (Java Topology Suite) libraries to read shapefiles and calculate polygon areas. For geographic coordinate systems, it automatically selects an appropriate UTM zone based on the center of the shapefile's extent to ensure accurate area calculations. The area calculation uses the JTS geometry area method, which handles both simple polygons and multipolygons correctly.


{title:Author}

{pstd}
Chunxia Chen{p_end}
{pstd}
School of Management, Xiamen University{p_end}
{pstd}
Email: triciachen6754@126.com{p_end}


{title:Also see}

{psee}
Online:  {help checkshp}, {help intershp}, {help reprojshp}
{p_end}
