// cd "D:\checkshp"

local shp `"fujian.shp"'
local shp2 `"fuzhou_building.shp"' 
local tif_file `"DMSP-like2020.tif"'

checkshp "`shp'", detail clean

reprojshp "`shp'", detail crs(EPSG:3857)

reprojshp "`shp'", crs("`tif_file'")

areashp "`shp'"

intershp "`shp'" with("`shp2'"),  group(Floor)


