cd "D:\checkshp"

local shp `"fujian.shp"'
local shp2 `"fuzhou_building.shp"' 
local tif_file `"DMSP-like2020.tif"'

checkshp "`shp'", detail clean

checkshp "`shp'", reproject(EPSG:4326)

checkshp "`shp'", reproject("`tif_file'")

checkshp "`shp'", intersect("`shp2'") group(Floor)


