package com.example.gcheckshp;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.Filter;
import java.util.logging.LogRecord;
import java.util.logging.Handler;
import org.geotools.api.data.FeatureWriter;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.data.Transaction;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.GeometryDescriptor;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.util.GeometryFixer;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.locationtech.jts.operation.valid.TopologyValidationError;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.crs.GeographicCRS;
import org.geotools.api.referencing.cs.CoordinateSystem;
import org.geotools.api.referencing.cs.CoordinateSystemAxis;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.data.DataUtilities;
import org.geotools.referencing.CRS;
import org.geotools.gce.geotiff.GeoTiffReader;

public class gcheckshp {
    // 是否提前合并shp2所有要素再参与交集计算，默认false（已移除静态变量，改为参数传递）

    // 推荐的STRtree节点容量（如100，经验值，适合大数据量）
    private static final int STRTREE_NODE_CAPACITY = 100;
    
    // CSV写入缓冲区大小（8KB）
    private static final int CSV_BUFFER_SIZE = 8192;
    
    // CSV字段分隔符
    private static final String CSV_SEPARATOR = ",";
    
    // 面积格式化精度
    private static final String AREA_FORMAT = "%.6f";

    private static final Logger logger = Logger.getLogger(gcheckshp.class.getName());

    // 配置日志系统，抑制 GeoTools 文件操作相关的警告
    private static void configureLogging() {
        try {
            // 创建自定义过滤器，过滤掉文件替换相关的警告
            Filter fileReplaceFilter = new Filter() {
                @Override
                public boolean isLoggable(LogRecord record) {
                    if (record == null) {
                        return true;
                    }
                    String message = record.getMessage();
                    // 过滤掉文件替换相关的警告消息
                    if (message != null && message.contains("Unable to delete the file") 
                            && message.contains("when attempting to replace with temporary copy")) {
                        return false; // 不记录这条日志
                    }
                    return true;
                }
            };
            
            // 获取根日志记录器并应用过滤器
            Logger rootLogger = Logger.getLogger("");
            for (Handler handler : rootLogger.getHandlers()) {
                handler.setFilter(fileReplaceFilter);
            }
            
            // 同时抑制 GeoTools StorageFile 类的所有日志
            Logger storageFileLogger = Logger.getLogger("org.geotools.data.shapefile.files.StorageFile");
            storageFileLogger.setLevel(Level.OFF);
            storageFileLogger.setFilter(fileReplaceFilter);
            
            // 抑制整个 shapefile 文件操作相关的日志
            Logger shapefileFilesLogger = Logger.getLogger("org.geotools.data.shapefile.files");
            shapefileFilesLogger.setLevel(Level.OFF);
            shapefileFilesLogger.setFilter(fileReplaceFilter);
            
        } catch (Exception e) {
            // 如果日志配置失败，不影响程序运行
            // 静默忽略
        }
    }

    // Entry point
    public static void main(String[] args) {
        // 配置日志级别，抑制 GeoTools 文件替换时的警告
        configureLogging();
        
        printArgs(args);
        if (args == null || args.length < 3) {
            printUsage();
            return;
        }
        if (isIntersectionMode(args)) {
            handleIntersectionMode(args);
            return;
        }
        handleCheckOrReprojectMode(args);
    }

    // Print received arguments for debugging
    private static void printArgs(String[] args) {
        if (args != null && args.length > 0) {
            System.out.println("Received " + args.length + " arguments:");
            for (int i = 0; i < args.length; i++) {
                System.out.println("  args[" + i + "] = " + args[i]);
            }
        }
    }

    // Print usage in English
    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println(
                "  Check mode: java -jar gcheckshp-core.jar <shpPath> <detail|summary> <true|false> [targetCRS]");
        System.out.println(
                "  Reproject mode: java -jar gcheckshp-core.jar <shpPath> <detail|summary> <true|false> <targetCRS>");
        System.out.println(
                "  Intersection stats: java -jar gcheckshp-core.jar <shp1> intersect <shp2> [--merge-shp2] [--group-field <fieldName>]");
    }

    // Determine if intersection mode
    private static boolean isIntersectionMode(String[] args) {
        return args.length >= 3 && "intersect".equalsIgnoreCase(args[1]);
    }

    // Handle intersection mode
    private static void handleIntersectionMode(String[] args) {
        String shp1 = args[0];
        
        // 检查shp1文件存在性
        File shp1File = new File(shp1);
        if (!shp1File.exists()) {
            System.out.println("Shapefile 1 does not exist: " + shp1);
            return;
        }
        if (!checkCompanionFiles(shp1File)) {
            return;
        }
        
        String shp2 = null;
        boolean mergeOption = false;
        String groupField = null;
        
        // 首先找到shp2路径（第一个.shp后缀的参数）
        for (int i = 2; i < args.length; i++) {
            if (args[i].toLowerCase().endsWith(".shp")) {
                shp2 = args[i];
                break; // 找到第一个.shp文件后停止
            }
        }
        
        // 然后处理其他选项参数
        for (int i = 2; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--merge-shp2")) {
                mergeOption = true;
                continue;
            }
            if (args[i].equalsIgnoreCase("--group-field")) {
                if (i + 1 < args.length) {
                    groupField = args[i + 1];
                    i++; // 跳过下一个参数，因为它是字段名
                } else {
                    System.out.println("Warning: --group-field requires a field name");
                }
                continue;
            }
        }
        
        // 检查shp2文件存在性
        if (shp2 == null || shp2.trim().isEmpty()) {
            System.out.println("Error: shp2 path is required");
            return;
        }
        File shp2File = new File(shp2);
        if (!shp2File.exists()) {
            System.out.println("Shapefile 2 does not exist: " + shp2);
            return;
        }
        if (!checkCompanionFiles(shp2File)) {
            return;
        }
        
        calculateIntersectionStats(shp1, shp2, groupField, mergeOption);
    }

    // Handle check or reproject mode
    private static void handleCheckOrReprojectMode(String[] args) {
        String shpPath = args[0];
        String detailFlag = args[1];
        String deleteFlag = args[2];
        String targetCRS = args.length > 3 ? args[3] : null;
        mainCheckOrReproject(shpPath, detailFlag, deleteFlag, targetCRS);
    }

    // Main logic for check/reproject
    public static void mainCheckOrReproject(String shpPath, String detailFlag, String deleteFlag, String targetCRS) {
        if (targetCRS != null && !targetCRS.isEmpty()) {
            try {
                int lastDot = shpPath.lastIndexOf('.');
                String outShp = lastDot > 0 ? shpPath.substring(0, lastDot) + "_reproj.shp" 
                        : shpPath + "_reproj.shp";
                reprojectShapefile(shpPath, outShp, targetCRS);
                System.out.println("Reprojected shapefile saved to: " + outShp);
                return;
            } catch (Exception ex) {
                System.out.println("Failed to reproject shapefile: " + ex.getMessage());
                ex.printStackTrace();
                return;
            }
        }
        File shpFile = new File(shpPath);
        if (!shpFile.exists()) {
            System.out.println("Shapefile does not exist: " + shpPath);
            System.out.println("Absolute path checked: " + shpFile.getAbsolutePath());
            // 尝试使用规范化路径，以处理路径中的编码问题
            try {
                String canonicalPath = shpFile.getCanonicalPath();
                File canonicalFile = new File(canonicalPath);
                if (canonicalFile.exists()) {
                    System.out.println("File found using canonical path: " + canonicalPath);
                    shpFile = canonicalFile;
                } else {
                    return;
                }
            } catch (java.io.IOException e) {
                System.out.println("Failed to get canonical path: " + e.getMessage());
                return;
            }
        }
        if (!checkCompanionFiles(shpFile)) {
            return;
        }
        boolean doDelete = "true".equalsIgnoreCase(deleteFlag);
        File workingShpFile = shpFile;
        if (doDelete) {
            workingShpFile = createWorkingCopy(shpFile);
            if (workingShpFile == null) {
                System.out.println("Unable to prepare a safe working copy; aborting delete operation.");
                return;
            }
            System.out.println("Working copy for delete operation: " + workingShpFile.getAbsolutePath());
        }
        processShapefile(workingShpFile, detailFlag, doDelete);
    }

    // Process shapefile for check/delete
    private static void processShapefile(File shpFile, String detailFlag, boolean doDelete) {
        ShapefileDataStore store = null;
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("url", shpFile.toURI().toURL());
            ShapefileDataStoreFactory factory = new ShapefileDataStoreFactory();
            store = (ShapefileDataStore) factory.createDataStore(params);
            store.setCharset(Charset.forName("UTF-8"));

            SimpleFeatureSource featureSource = store.getFeatureSource();
            SimpleFeatureCollection collection = featureSource.getFeatures();

            SimpleFeatureType schema = collection.getSchema();
            GeometryDescriptor geomDescriptor = schema != null ? schema.getGeometryDescriptor() : null;
            String geometryName = geomDescriptor != null ? geomDescriptor.getLocalName() : "<unknown>";
            String geometryType = geomDescriptor != null ? geomDescriptor.getType().getName().toString() : "<unknown>";
            boolean showDetail = "detail".equalsIgnoreCase(detailFlag);

            ReferencedEnvelope bounds = collection.getBounds();

            GeometryStats stats = new GeometryStats();
            int deletedGeometries = 0;
            List<String> issueDetails = new ArrayList<>();

            if (doDelete) {
                Transaction deleteTransaction = new DefaultTransaction("delete-invalid");
                try (FeatureWriter<SimpleFeatureType, SimpleFeature> writer = 
                        store.getFeatureWriter(store.getTypeNames()[0], deleteTransaction)) {
                    while (writer.hasNext()) {
                        SimpleFeature feature = writer.next();
                        boolean removeFeature = shouldRemoveFeature(feature, showDetail, issueDetails, stats);
                        if (removeFeature) {
                            writer.remove();
                            deletedGeometries++;
                        } else {
                            writer.write();
                        }
                    }
                    deleteTransaction.commit();
                } catch (Exception e) {
                    deleteTransaction.rollback();
                    throw e;
                } finally {
                    deleteTransaction.close();
                }
            } else {
                try (SimpleFeatureIterator iterator = collection.features()) {
                    while (iterator.hasNext()) {
                        SimpleFeature feature = iterator.next();
                        shouldRemoveFeature(feature, showDetail, issueDetails, stats);
                    }
                }
            }

            System.out.println("Shapefile path: " + shpFile.getAbsolutePath());
            System.out.println("Geometry field: " + geometryName);
            System.out.println("Geometry type: " + geometryType);
            System.out.println("Feature count: " + stats.total);
            System.out.println("Null geometries: " + stats.nullGeometry);
            System.out.println("Empty geometries: " + stats.emptyGeometry);
            System.out.println("Invalid geometries: " + stats.invalidGeometry);
            System.out.println("Invalid but auto-fixable geometries: " + stats.fixableGeometry);

            if (bounds != null && !bounds.isEmpty()) {
                Envelope env = new Envelope(bounds.getMinX(), bounds.getMaxX(), bounds.getMinY(), bounds.getMaxY());
                System.out.println("Extent: [" + env.getMinX() + ", " + env.getMinY() + "] -> [" + env.getMaxX() + ", "
                        + env.getMaxY() + "]");
            } else {
                System.out.println("Extent: unavailable (empty bounds)");
            }

            if (showDetail && !issueDetails.isEmpty()) {
                System.out.println("--- Detailed Issues ---");
                for (String line : issueDetails) {
                    System.out.println(line);
                }
            }

            if (doDelete) {
                System.out.println("Features removed via delete option: " + deletedGeometries);
                if (deletedGeometries == 0) {
                    System.out.println("No features met the delete criteria; no changes were written.");
                } else {
                    System.out.println("Invalid features removed in place; original schema and encoding preserved.");
                    System.out.println("Clean shapefile saved to: " + shpFile.getAbsolutePath());
                }
            }

        } catch (Exception e) {
            System.out.println("Failed to inspect shapefile: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (store != null) {
                store.dispose();
            }
        }
    }

    // Intersection statistics (refactored)
    // 只输出交叠面积和数量，CSV包含shp1所有字段

    public static void calculateIntersectionStats(String shp1, String shp2, String groupField,
            boolean mergeShp2BeforeIntersect) {
        ShapefileDataStore store1 = null;
        ShapefileDataStore store2 = null;
        try {
            File file1 = new File(shp1);
            // 文件存在性检查已在handleIntersectionMode中完成，此处跳过
            Map<String, Object> params1 = new HashMap<>();
            params1.put("url", file1.toURI().toURL());
            ShapefileDataStoreFactory factory = new ShapefileDataStoreFactory();
            store1 = (ShapefileDataStore) factory.createDataStore(params1);
            store1.setCharset(Charset.forName("UTF-8"));
            SimpleFeatureSource featureSource1 = store1.getFeatureSource();
            SimpleFeatureCollection collection1 = featureSource1.getFeatures();
            CoordinateReferenceSystem crs1 = store1.getSchema().getCoordinateReferenceSystem();

            File file2 = new File(shp2);
            // 文件存在性检查已在handleIntersectionMode中完成，此处跳过
            Map<String, Object> params2 = new HashMap<>();
            params2.put("url", file2.toURI().toURL());
            store2 = (ShapefileDataStore) factory.createDataStore(params2);
            store2.setCharset(Charset.forName("UTF-8"));
            SimpleFeatureSource featureSource2 = store2.getFeatureSource();
            SimpleFeatureCollection collection2 = featureSource2.getFeatures();
            CoordinateReferenceSystem crs2 = store2.getSchema().getCoordinateReferenceSystem();

            // 计算 bounds1
            ReferencedEnvelope bounds1 = collection1.getBounds();

            // 确定用于面积计算的坐标系
            CoordinateReferenceSystem workingCRS = crs1 != null ? crs1 : crs2;
            CoordinateReferenceSystem areaCalculationCRS = null;
            MathTransform transform1 = null;
            MathTransform transform2 = null;
            String areaUnit = "unknown";

            if (workingCRS == null) {
                System.out.println("Warning: Both shapefiles missing CRS. Cannot calculate accurate area.");
            } else {
                boolean isGeographic = isGeographicCRS(workingCRS);
                if (isGeographic) {
                    System.out.println("Detected geographic CRS. Converting to projected CRS for area calculation.");
                    System.out.println("  Original CRS: " + workingCRS.getName());
                    ReferencedEnvelope bounds2 = collection2.getBounds();
                    double centerLon = (bounds1.getMinX() + bounds1.getMaxX() + bounds2.getMinX() + bounds2.getMaxX())
                            / 4.0;
                    double centerLat = (bounds1.getMinY() + bounds1.getMaxY() + bounds2.getMinY() + bounds2.getMaxY())
                            / 4.0;
                    int utmZone = (int) Math.floor((centerLon + 180) / 6) + 1;
                    String utmCode = centerLat >= 0 ? "EPSG:326" + String.format("%02d", utmZone)
                            : "EPSG:327" + String.format("%02d", utmZone);
                    try {
                        areaCalculationCRS = CRS.decode(utmCode, true);
                        System.out.println("  Using UTM CRS for area calculation: " + areaCalculationCRS.getName());
                        transform1 = CRS.findMathTransform(workingCRS, areaCalculationCRS, true);
                        if (crs2 != null && !CRS.equalsIgnoreMetadata(workingCRS, crs2)) {
                            transform2 = CRS.findMathTransform(crs2, areaCalculationCRS, true);
                        } else {
                            transform2 = transform1;
                        }
                        areaUnit = "square meters";
                    } catch (Exception e) {
                        System.out.println(
                                "Warning: Failed to create UTM projection. Using original CRS: " + e.getMessage());
                        areaCalculationCRS = workingCRS;
                        areaUnit = "degrees² (not accurate for area)";
                    }
                } else {
                    areaCalculationCRS = workingCRS;
                    try {
                        CoordinateSystem cs = workingCRS.getCoordinateSystem();
                        if (cs != null && cs.getDimension() > 0) {
                            CoordinateSystemAxis axis = cs.getAxis(0);
                            if (axis != null) {
                                String unit = axis.getUnit().toString();
                                if (unit.toLowerCase().contains("metre") || unit.toLowerCase().contains("meter")) {
                                    areaUnit = "square meters";
                                } else {
                                    areaUnit = unit + "²";
                                }
                            }
                        }
                        if ("unknown".equals(areaUnit)) {
                            areaUnit = "square units";
                        }
                    } catch (Exception e) {
                        areaUnit = "square units";
                    }
                    if (crs1 != null && crs2 != null && !CRS.equalsIgnoreMetadata(crs1, crs2)) {
                        System.out.println("Warning: CRS mismatch. Reprojecting shp2 to shp1's CRS.");
                        System.out.println("  shp1 CRS: " + crs1.getName());
                        System.out.println("  shp2 CRS: " + crs2.getName());
                        transform2 = CRS.findMathTransform(crs2, crs1, true);
                        transform1 = null;
                    }
                }
            }

            // Validate groupField existence
            if (groupField != null && !validateGroupField(store2, groupField)) {
                System.err.println("Error: Group field '" + groupField
                        + "' does not exist in shp2 attributes. Operation stopped.");
                logger.severe("Group field '" + groupField + "' does not exist in shp2 attributes.");
                return; // 停止执行
            }

            // Build spatial index and group map (only when groupField is specified)
            Map<String, List<Geometry>> groupGeoms = null;
            // 建立几何到分组的映射，用于分组统计（仅在启用分组时创建）
            Map<Geometry, String> geomToGroup = null;
            // 收集所有唯一的分组值，用于生成CSV列名
            final List<String> uniqueGroupValues = groupField != null ? new ArrayList<>() : null;
            if (groupField != null) {
                groupGeoms = new HashMap<>();
                geomToGroup = new HashMap<>();
            }
            org.locationtech.jts.index.strtree.STRtree strTree = null;
            Geometry mergedShp2 = null;
            int shp2GeomCount = 0;
            List<Geometry> shp2PolygonGeoms = new ArrayList<>();
            try (SimpleFeatureIterator iterator2 = collection2.features()) {
                while (iterator2.hasNext()) {
                    SimpleFeature feature = iterator2.next();
                    Object geomObj = feature.getDefaultGeometry();
                    if (geomObj instanceof Geometry) {
                        Geometry geom = (Geometry) geomObj;
                        if (!geom.isEmpty() && geom.isValid()) {
                            if (transform2 != null) {
                                try {
                                    geom = org.geotools.geometry.jts.JTS.transform(geom, transform2);
                                } catch (Exception e) {
                                    logger.warning("Failed to transform geometry from shp2: " + e.getMessage());
                                    continue;
                                }
                            }
                            String geomType = geom.getGeometryType();
                            if ("Polygon".equalsIgnoreCase(geomType) || "MultiPolygon".equalsIgnoreCase(geomType)) {
                                shp2PolygonGeoms.add(geom);
                                // 仅在启用分组统计时记录分组信息
                                if (groupField != null && groupGeoms != null && geomToGroup != null && uniqueGroupValues != null) {
                                    Object attr = feature.getAttribute(groupField);
                                    String key = (attr != null) ? attr.toString() : "<null>";
                                    groupGeoms.computeIfAbsent(key, k -> {
                                        uniqueGroupValues.add(key); // 收集唯一的分组值
                                        return new ArrayList<>();
                                    }).add(geom);
                                    geomToGroup.put(geom, key);
                                }
                            }
                        }
                        shp2GeomCount++;
                        // 移除索引进度日志，避免控制台输出过于杂乱
                        // if (shp2GeomCount % 1000 == 0) {
                        //     logger.info("Indexed " + shp2GeomCount + " geometries from shp2...");
                        // }
                    }
                }
            }
            if (shp2GeomCount == 0) {
                logger.severe("shp2 has no valid geometries.");
                return;
            }
            if (shp2PolygonGeoms.isEmpty()) {
                logger.severe("shp2 contains no Polygon/MultiPolygon geometries.");
                return;
            }
            // 如果既 merge 又 group，按分组分别 merge；如果只 merge 不 group，全局 merge；如果都不，构建索引
            Map<String, Geometry> mergedGroupGeoms = null;
            if (mergeShp2BeforeIntersect && groupField != null && groupGeoms != null) {
                // 按分组分别 merge
                mergedGroupGeoms = new HashMap<>();
                for (Map.Entry<String, List<Geometry>> entry : groupGeoms.entrySet()) {
                    String groupKey = entry.getKey();
                    List<Geometry> groupGeomList = entry.getValue();
                    if (!groupGeomList.isEmpty()) {
                        try {
                            Geometry mergedGroup = org.locationtech.jts.operation.union.CascadedPolygonUnion.union(groupGeomList);
                            if (mergedGroup != null && !mergedGroup.isEmpty()) {
                                mergedGroupGeoms.put(groupKey, mergedGroup);
                            }
                        } catch (Exception e) {
                            logger.warning("Failed to merge group '" + groupKey + "': " + e.getMessage());
                        }
                    }
                }
                if (mergedGroupGeoms.isEmpty()) {
                    logger.severe("All group merges failed or resulted in empty geometries.");
                    return;
                }
                // 同时计算总的合并结果（用于总体交集面积统计）
                mergedShp2 = org.locationtech.jts.operation.union.CascadedPolygonUnion.union(shp2PolygonGeoms);
                if (mergedShp2 == null || mergedShp2.isEmpty()) {
                    logger.severe("shp2 merge failed or result is empty.");
                    return;
                }
            } else if (mergeShp2BeforeIntersect) {
                // 只 merge 不 group，全局 merge
                mergedShp2 = org.locationtech.jts.operation.union.CascadedPolygonUnion.union(shp2PolygonGeoms);
                if (mergedShp2 == null || mergedShp2.isEmpty()) {
                    logger.severe("shp2 merge failed or result is empty.");
                    return;
                }
            } else {
                // 不 merge，构建空间索引
                strTree = new org.locationtech.jts.index.strtree.STRtree(STRTREE_NODE_CAPACITY);
                for (Geometry geom : shp2PolygonGeoms) {
                    strTree.insert(geom.getEnvelopeInternal(), geom);
                }
            }

            // 获取shp1所有非几何字段名
            SimpleFeatureType featureType1 = store1.getSchema();
            List<String> shp1FieldNames = new ArrayList<>();
            for (int i = 0; i < featureType1.getAttributeCount(); i++) {
                String name = featureType1.getDescriptor(i).getLocalName();
                // 排除几何字段（如 the_geom）
                if (!(featureType1.getDescriptor(i) instanceof GeometryDescriptor)) {
                    shp1FieldNames.add(name);
                }
            }

            // 生成 CSV 文件名（基于第一个 shp 文件名）
            String csvFileName = file1.getName();
            int dotIndex = csvFileName.lastIndexOf('.');
            String baseName = dotIndex > 0 ? csvFileName.substring(0, dotIndex) : csvFileName;
            File csvFile = new File(file1.getParent(), baseName + "_intersection_stats.csv");

            // 对分组值进行排序，确保列的顺序一致
            if (uniqueGroupValues != null && !uniqueGroupValues.isEmpty()) {
                uniqueGroupValues.sort((a, b) -> {
                    // 尝试按数字排序，如果不能转换则按字符串排序
                    try {
                        double numA = Double.parseDouble(a);
                        double numB = Double.parseDouble(b);
                        return Double.compare(numA, numB);
                    } catch (NumberFormatException e) {
                        return a.compareTo(b);
                    }
                });
            }

            // Compute intersections and write to CSV in streaming mode (避免内存占用过大)
            double totalIntersectionArea;
            if (mergeShp2BeforeIntersect && groupField != null && mergedGroupGeoms != null) {
                // 既 merge 又 group：使用按分组合并的结果
                totalIntersectionArea = computeIntersectionsWithMergedGroupsAndWrite(collection1, transform1, 
                        shp1FieldNames, mergedShp2, mergedGroupGeoms, csvFile, groupField, uniqueGroupValues);
            } else if (mergeShp2BeforeIntersect) {
                // 只 merge 不 group：使用全局合并结果
                totalIntersectionArea = computeIntersectionsWithMergedShp2AndWrite(collection1, transform1, 
                        shp1FieldNames, mergedShp2, csvFile, null, null, null);
            } else {
                // 不 merge：使用索引
                totalIntersectionArea = computeIntersectionsWithAttributesAndWrite(collection1, transform1, 
                        shp1FieldNames, strTree, csvFile, groupField, 
                        groupField != null ? geomToGroup : null,
                        uniqueGroupValues);
            }

            // 输出统计结果到控制台
            System.out.println("=== Intersection Statistics ===");
            System.out.println("shp1: " + file1.getAbsolutePath());
            System.out.println("shp2: " + file2.getAbsolutePath());
            System.out.println("shp1 feature count: " + collection1.size());
            System.out.println("shp2 feature count: " + shp2GeomCount);
            System.out.println("--- Area Calculation Settings ---");
            System.out.println("Coordinate System (CRS): "
                    + (areaCalculationCRS != null ? areaCalculationCRS.getName().toString() : "unknown"));
            System.out.println("Area Unit: " + areaUnit);
            System.out.println("Total intersection area: " + String.format("%.6f", totalIntersectionArea) + " " + areaUnit);
            System.out.println("CSV file saved to: " + csvFile.getAbsolutePath());
        } catch (Exception e) {
            System.out.println("Failed to calculate intersection statistics: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (store1 != null)
                store1.dispose();
            if (store2 != null)
                store2.dispose();
        }
    }

    // 判断CRS是否为地理坐标系
    private static boolean isGeographicCRS(CoordinateReferenceSystem crs) {
        if (crs == null)
            return false;
        return crs instanceof GeographicCRS;
    }

    private static boolean validateGroupField(ShapefileDataStore store, String groupField) {
        try {
            SimpleFeatureType schema = store.getSchema();
            return schema.getDescriptor(groupField) != null;
        } catch (java.io.IOException e) {
            logger.warning("Failed to get schema from store: " + e.getMessage());
            return false;
        }
    }

    // 交叠统计，流式写入CSV，输出shp1所有字段+feature_area+intersection_area+intersecting_shp2_count
    private static double computeIntersectionsWithAttributesAndWrite(SimpleFeatureCollection collection1,
            MathTransform transform1, List<String> shp1FieldNames,
            org.locationtech.jts.index.strtree.STRtree strTree, File csvFile, 
            String groupField, Map<Geometry, String> geomToGroup, List<String> uniqueGroupValues) {
        double totalIntersectionArea = 0.0;
        try (PrintWriter writer = new PrintWriter(
                new java.io.BufferedWriter(new FileWriter(csvFile, false), CSV_BUFFER_SIZE));
                SimpleFeatureIterator iterator1 = collection1.features()) {
            // 写入表头
            writeCsvHeader(writer, shp1FieldNames, groupField, uniqueGroupValues);
            
            while (iterator1.hasNext()) {
                SimpleFeature feature = iterator1.next();
                Object geomObj = feature.getDefaultGeometry();
                if (geomObj instanceof Geometry) {
                    Geometry geom = (Geometry) geomObj;
                    if (!geom.isEmpty() && geom.isValid()) {
                        geom = transformGeometry(geom, transform1, feature.getID());
                        if (geom == null) {
                            continue;
                        }
                        
                        double featureArea = geom.getArea();
                        Envelope geomEnv = geom.getEnvelopeInternal();
                        IntersectionResult result = calculateIntersections(geom, geomEnv, strTree, 
                                feature.getID(), groupField, geomToGroup);
                        
                        totalIntersectionArea += result.area;
                        writeCsvRow(writer, feature, shp1FieldNames, featureArea, result.area, 
                                result.count, groupField, result.groupStats, uniqueGroupValues);
                    }
                }
            }
        } catch (IOException e) {
            logger.warning("Failed to write CSV file: " + e.getMessage());
        }
        return totalIntersectionArea;
    }
    
    // 计算与STRtree中几何的交集
    private static IntersectionResult calculateIntersections(Geometry geom, Envelope geomEnv,
            org.locationtech.jts.index.strtree.STRtree strTree, String featureId,
            String groupField, Map<Geometry, String> geomToGroup) {
        double intersectionArea = 0.0;
        int intersectingShp2Count = 0;
        Map<String, GroupStats> groupStats = groupField != null ? new HashMap<>() : null;
        List<?> possibleGeoms = strTree.query(geomEnv);
        
        for (Object obj : possibleGeoms) {
            Geometry shp2Geom = (Geometry) obj;
            Envelope shp2Env = shp2Geom.getEnvelopeInternal();
            // 先检查envelope是否相交，避免不必要的几何计算
            if (shp2Env.intersects(geomEnv)) {
                // 进一步检查几何是否真正相交
                if (shp2Geom.intersects(geom)) {
                    try {
                        Geometry intersection = geom.intersection(shp2Geom);
                        if (intersection != null && !intersection.isEmpty()) {
                            double interArea = intersection.getArea();
                            intersectionArea += interArea;
                            intersectingShp2Count++;
                            
                            // 如果启用了分组统计，记录分组信息
                            if (groupField != null && geomToGroup != null && groupStats != null) {
                                String groupKey = geomToGroup.get(shp2Geom);
                                if (groupKey != null) {
                                    groupStats.computeIfAbsent(groupKey, k -> new GroupStats())
                                            .addArea(interArea);
                                    groupStats.get(groupKey).incrementCount();
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.warning("Failed to calculate intersection for feature " + featureId
                                + ": " + e.getMessage());
                    }
                }
            }
        }
        return new IntersectionResult(intersectionArea, intersectingShp2Count, groupStats);
    }
    
    // 交集结果封装类
    private static class IntersectionResult {
        final double area;
        final int count;
        final Map<String, GroupStats> groupStats;
        
        IntersectionResult(double area, int count, Map<String, GroupStats> groupStats) {
            this.area = area;
            this.count = count;
            this.groupStats = groupStats;
        }
    }
    
    // 分组统计信息
    private static class GroupStats {
        double area = 0.0;
        int count = 0;
        
        void addArea(double area) {
            this.area += area;
        }
        
        void incrementCount() {
            this.count++;
        }
    }
    
    // 转换几何坐标系
    private static Geometry transformGeometry(Geometry geom, MathTransform transform, String featureId) {
        if (transform != null) {
            try {
                return org.geotools.geometry.jts.JTS.transform(geom, transform);
            } catch (Exception e) {
                logger.warning("Failed to transform geometry from shp1 (feature " + featureId + "): " + e.getMessage());
                return null;
            }
        }
        return geom;
    }
    
    // 写入CSV表头
    private static void writeCsvHeader(PrintWriter writer, List<String> shp1FieldNames, String groupField, List<String> uniqueGroupValues) {
        StringBuilder header = new StringBuilder(shp1FieldNames.size() * 10 + 50); // 预分配容量
        for (String field : shp1FieldNames) {
            header.append(field).append(CSV_SEPARATOR);
        }
        header.append("Feature_Area").append(CSV_SEPARATOR)
              .append("Intersection_Area").append(CSV_SEPARATOR)
              .append("Intersecting_Shp2_Count");
        // 为每个分组值生成两列：Area 和 Count
        if (groupField != null && uniqueGroupValues != null && !uniqueGroupValues.isEmpty()) {
            for (String groupValue : uniqueGroupValues) {
                String safeValue = escapeCsvColumnName(groupValue);
                header.append(CSV_SEPARATOR).append(groupField).append("_").append(safeValue).append("_Area");
                header.append(CSV_SEPARATOR).append(groupField).append("_").append(safeValue).append("_Count");
            }
        }
        writer.println(header.toString());
    }
    
    // 写入CSV行
    private static void writeCsvRow(PrintWriter writer, SimpleFeature feature, List<String> shp1FieldNames,
            double featureArea, double intersectionArea, int intersectingShp2Count,
            String groupField, Map<String, GroupStats> groupStats, List<String> uniqueGroupValues) {
        StringBuilder line = new StringBuilder(shp1FieldNames.size() * 15 + 50); // 预分配容量
        for (String fieldName : shp1FieldNames) {
            Object val = feature.getAttribute(fieldName);
            line.append(escapeCsv(val != null ? val.toString() : "")).append(CSV_SEPARATOR);
        }
        line.append(String.format(AREA_FORMAT, featureArea)).append(CSV_SEPARATOR)
            .append(String.format(AREA_FORMAT, intersectionArea)).append(CSV_SEPARATOR)
            .append(intersectingShp2Count);
        // 按照分组值的顺序，为每个分组值输出 Area 和 Count 两列
        if (groupField != null && uniqueGroupValues != null && !uniqueGroupValues.isEmpty()) {
            for (String groupValue : uniqueGroupValues) {
                GroupStats stats = groupStats != null ? groupStats.get(groupValue) : null;
                if (stats != null) {
                    line.append(CSV_SEPARATOR).append(String.format(AREA_FORMAT, stats.area));
                    line.append(CSV_SEPARATOR).append(stats.count);
                } else {
                    line.append(CSV_SEPARATOR).append("0.000000"); // 面积为空时输出0
                    line.append(CSV_SEPARATOR).append("0"); // 数量为空时输出0
                }
            }
        }
        writer.println(line.toString());
    }

    // 既 merge 又 group：按分组合并后交叠统计，流式写入CSV
    private static double computeIntersectionsWithMergedGroupsAndWrite(SimpleFeatureCollection collection1,
            MathTransform transform1, List<String> shp1FieldNames, Geometry mergedShp2, 
            Map<String, Geometry> mergedGroupGeoms, File csvFile, String groupField, List<String> uniqueGroupValues) {
        double totalIntersectionArea = 0.0;
        Envelope mergedEnv = mergedShp2.getEnvelopeInternal();
        
        try (PrintWriter writer = new PrintWriter(
                new java.io.BufferedWriter(new FileWriter(csvFile, false), CSV_BUFFER_SIZE));
                SimpleFeatureIterator iterator1 = collection1.features()) {
            // 写入表头
            writeCsvHeader(writer, shp1FieldNames, groupField, uniqueGroupValues);
            
            while (iterator1.hasNext()) {
                SimpleFeature feature = iterator1.next();
                Object geomObj = feature.getDefaultGeometry();
                if (geomObj instanceof Geometry) {
                    Geometry geom = (Geometry) geomObj;
                    if (!geom.isEmpty() && geom.isValid()) {
                        geom = transformGeometry(geom, transform1, feature.getID());
                        if (geom == null) {
                            continue;
                        }
                        
                        double featureArea = geom.getArea();
                        double intersectionArea = 0.0;
                        int intersectingShp2Count = 0;
                        Map<String, GroupStats> groupStats = new HashMap<>();
                        
                        Envelope geomEnv = geom.getEnvelopeInternal();
                        // 先检查envelope是否相交，避免不必要的几何计算
                        if (mergedEnv.intersects(geomEnv) && mergedShp2.intersects(geom)) {
                            try {
                                Geometry intersection = geom.intersection(mergedShp2);
                                if (intersection != null && !intersection.isEmpty()) {
                                    intersectionArea = intersection.getArea();
                                    intersectingShp2Count = 1;
                                    
                                    // 使用按分组合并后的几何体计算每个分组的交集
                                    for (Map.Entry<String, Geometry> entry : mergedGroupGeoms.entrySet()) {
                                        String groupKey = entry.getKey();
                                        Geometry mergedGroupGeom = entry.getValue();
                                        
                                        if (mergedGroupGeom.getEnvelopeInternal().intersects(geomEnv) 
                                                && mergedGroupGeom.intersects(geom)) {
                                            try {
                                                Geometry groupIntersection = geom.intersection(mergedGroupGeom);
                                                if (groupIntersection != null && !groupIntersection.isEmpty()) {
                                                    GroupStats stats = new GroupStats();
                                                    stats.area = groupIntersection.getArea();
                                                    stats.count = 1; // 已合并，count 为 1
                                                    groupStats.put(groupKey, stats);
                                                }
                                            } catch (Exception e) {
                                                // 忽略单个分组的计算错误
                                                logger.warning("Failed to calculate intersection for group '" + groupKey 
                                                        + "': " + e.getMessage());
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                logger.warning("Failed to calculate intersection for feature " + feature.getID()
                                        + ": " + e.getMessage());
                            }
                        }
                        
                        totalIntersectionArea += intersectionArea;
                        writeCsvRow(writer, feature, shp1FieldNames, featureArea, intersectionArea, 
                                intersectingShp2Count, groupField, groupStats, uniqueGroupValues);
                    }
                }
            }
        } catch (IOException e) {
            logger.warning("Failed to write CSV file: " + e.getMessage());
        }
        return totalIntersectionArea;
    }

    // 合并shp2后交叠统计，流式写入CSV（仅 merge 不 group 的情况）
    private static double computeIntersectionsWithMergedShp2AndWrite(SimpleFeatureCollection collection1,
            MathTransform transform1, List<String> shp1FieldNames, Geometry mergedShp2, File csvFile,
            String groupField, Map<String, List<Geometry>> groupGeoms, List<String> uniqueGroupValues) {
        double totalIntersectionArea = 0.0;
        Envelope mergedEnv = mergedShp2.getEnvelopeInternal();
        
        try (PrintWriter writer = new PrintWriter(
                new java.io.BufferedWriter(new FileWriter(csvFile, false), CSV_BUFFER_SIZE));
                SimpleFeatureIterator iterator1 = collection1.features()) {
            // 写入表头
            writeCsvHeader(writer, shp1FieldNames, groupField, uniqueGroupValues);
            
            while (iterator1.hasNext()) {
                SimpleFeature feature = iterator1.next();
                Object geomObj = feature.getDefaultGeometry();
                if (geomObj instanceof Geometry) {
                    Geometry geom = (Geometry) geomObj;
                    if (!geom.isEmpty() && geom.isValid()) {
                        geom = transformGeometry(geom, transform1, feature.getID());
                        if (geom == null) {
                            continue;
                        }
                        
                        double featureArea = geom.getArea();
                        double intersectionArea = 0.0;
                        int intersectingShp2Count = 0;
                        Map<String, GroupStats> groupStats = null;
                        
                        Envelope geomEnv = geom.getEnvelopeInternal();
                        // 先检查envelope是否相交，避免不必要的几何计算
                        if (mergedEnv.intersects(geomEnv) && mergedShp2.intersects(geom)) {
                            try {
                                Geometry intersection = geom.intersection(mergedShp2);
                                if (intersection != null && !intersection.isEmpty()) {
                                    intersectionArea = intersection.getArea();
                                    intersectingShp2Count = 1;
                                    
                                    // 仅在启用分组统计时计算每个分组的交集
                                    if (groupField != null && groupGeoms != null) {
                                        groupStats = new HashMap<>();
                                        for (Map.Entry<String, List<Geometry>> entry : groupGeoms.entrySet()) {
                                            String groupKey = entry.getKey();
                                            List<Geometry> groupGeomList = entry.getValue();
                                            double groupArea = 0.0;
                                            int groupCount = 0;
                                            
                                            for (Geometry groupGeom : groupGeomList) {
                                                if (groupGeom.getEnvelopeInternal().intersects(geomEnv) 
                                                        && groupGeom.intersects(geom)) {
                                                    try {
                                                        Geometry groupIntersection = geom.intersection(groupGeom);
                                                        if (groupIntersection != null && !groupIntersection.isEmpty()) {
                                                            groupArea += groupIntersection.getArea();
                                                            groupCount++;
                                                        }
                                                    } catch (Exception e) {
                                                        // 忽略单个分组的计算错误
                                                    }
                                                }
                                            }
                                            
                                            if (groupCount > 0) {
                                                GroupStats stats = new GroupStats();
                                                stats.area = groupArea;
                                                stats.count = groupCount;
                                                groupStats.put(groupKey, stats);
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                logger.warning("Failed to calculate intersection for feature " + feature.getID()
                                        + ": " + e.getMessage());
                            }
                        }
                        
                        totalIntersectionArea += intersectionArea;
                        writeCsvRow(writer, feature, shp1FieldNames, featureArea, intersectionArea, 
                                intersectingShp2Count, groupField, groupStats, uniqueGroupValues);
                    }
                }
            }
        } catch (IOException e) {
            logger.warning("Failed to write CSV file: " + e.getMessage());
        }
        return totalIntersectionArea;
    }


    /**
     * 对shapefile进行重投影
     * 
     * @param srcShp    输入shp路径
     * @param outShp    输出shp路径
     * @param targetCRS EPSG:xxxx 或 tif/nc文件路径
     */
    public static void reprojectShapefile(String srcShp, String outShp, String targetCRS) throws Exception {
        CoordinateReferenceSystem targetCRSObj = resolveTargetCRS(targetCRS);
        File srcFile = new File(srcShp);
        Map<String, Object> params = new HashMap<>();
        params.put("url", srcFile.toURI().toURL());
        ShapefileDataStoreFactory factory = new ShapefileDataStoreFactory();
        Map<String, Object> outParams = new HashMap<>();
        outParams.put("url", new File(outShp).toURI().toURL());
        ShapefileDataStore srcStore = null;
        ShapefileDataStore outStore = null;
        try {
            srcStore = (ShapefileDataStore) factory.createDataStore(params);
            outStore = (ShapefileDataStore) factory.createDataStore(outParams);
            srcStore.setCharset(Charset.forName("UTF-8"));
            outStore.setCharset(Charset.forName("UTF-8"));
            SimpleFeatureSource srcFeatureSource = srcStore.getFeatureSource();
            SimpleFeatureCollection srcCollection = srcFeatureSource.getFeatures();
            SimpleFeatureType srcSchema = srcCollection.getSchema();
            CoordinateReferenceSystem sourceCRS = srcStore.getSchema().getCoordinateReferenceSystem();
            if (sourceCRS == null) {
                throw new Exception("Source shapefile missing CRS definition (no .prj file?)");
            }
            MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRSObj, true);
            SimpleFeatureType targetSchema = DataUtilities.createSubType(srcSchema, null, targetCRSObj);
            outStore.createSchema(targetSchema);
            Transaction transaction = new DefaultTransaction("reproject");
            try (FeatureWriter<SimpleFeatureType, SimpleFeature> writer = 
                    outStore.getFeatureWriterAppend(transaction);
                    SimpleFeatureIterator it = srcCollection.features()) {
                while (it.hasNext()) {
                    SimpleFeature srcFeature = it.next();
                    SimpleFeature newFeature = writer.next();
                    for (int i = 0; i < srcFeature.getAttributeCount(); i++) {
                        Object attr = srcFeature.getAttribute(i);
                        if (attr instanceof Geometry) {
                            Geometry geom = (Geometry) attr;
                            Geometry newGeom = org.geotools.geometry.jts.JTS.transform(geom, transform);
                            newFeature.setAttribute(i, newGeom);
                        } else {
                            newFeature.setAttribute(i, attr);
                        }
                    }
                    writer.write();
                }
                transaction.commit();
            } catch (Exception e) {
                transaction.rollback();
                throw e;
            } finally {
                transaction.close();
            }
        } finally {
            if (srcStore != null)
                srcStore.dispose();
            if (outStore != null)
                outStore.dispose();
        }
    }

    private static boolean checkCompanionFiles(File shpFile) {
        String basePath = shpFile.getAbsolutePath();
        int dot = basePath.lastIndexOf('.');
        if (dot <= 0) {
            System.out.println("Shapefile must have an extension");
            return false;
        }
        String prefix = basePath.substring(0, dot);
        File shxFile = new File(prefix + ".shx");
        File dbfFile = new File(prefix + ".dbf");
        File prjFile = new File(prefix + ".prj");

        boolean ok = true;
        if (!shxFile.exists()) {
            System.out.println("Missing companion file: " + shxFile.getName());
            ok = false;
        }
        if (!dbfFile.exists()) {
            System.out.println("Missing companion file: " + dbfFile.getName());
            ok = false;
        }
        if (!prjFile.exists()) {
            System.out.println("Missing companion file: " + prjFile.getName());
            ok = false;
        }
        return ok;
    }

    private static File createWorkingCopy(File original) {
        File cleanFile = buildCleanFile(original);
        if (cleanFile == null) {
            return null;
        }
        
        // 检查清理文件是否已存在
        if (cleanFile.exists()) {
            System.out.println("Warning: Clean file already exists: " + cleanFile.getAbsolutePath());
            System.out.println("The existing clean file may cause conflicts. Please delete it first or use a different output name.");
            System.out.println("Required files to delete:");
            String[] extensions = { ".shp", ".shx", ".dbf", ".prj", ".cpg", ".fix", ".qix" };
            for (String ext : extensions) {
                File companionFile = companion(cleanFile, ext);
                if (companionFile.exists()) {
                    System.out.println("  - " + companionFile.getName());
                }
            }
            return null;
        }
        
        if (!copyShapefileFiles(original, cleanFile)) {
            return null;
        }
        return cleanFile;
    }

    private static File buildCleanFile(File original) {
        String name = original.getName();
        int dot = name.lastIndexOf('.');
        String prefix = dot > 0 ? name.substring(0, dot) : name;
        File parent = original.getParentFile() != null ? original.getParentFile() : new File(".");
        return new File(parent, prefix + "_clean.shp");
    }

    private static boolean copyShapefileFiles(File sourceShp, File targetShp) {
        String[] extensions = { ".shp", ".shx", ".dbf", ".prj", ".cpg" };
        for (String ext : extensions) {
            File source = companion(sourceShp, ext);
            File target = companion(targetShp, ext);
            if (!source.exists()) {
                if (".shp".equals(ext) || ".shx".equals(ext) || ".dbf".equals(ext)) {
                    System.out.println("Missing companion file during copy: " + source.getName());
                    return false;
                }
                deleteIfExists(target);
                continue;
            }
            deleteIfExists(target);
            try {
                Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                System.out.println(
                        "Failed to copy file: " + source.getAbsolutePath() + " -> " + target.getAbsolutePath());
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    private static File companion(File shpFile, String extension) {
        // File automatically handles / and \ as path separators on all platforms
        String path = shpFile.getAbsolutePath();
        int dot = path.lastIndexOf('.');
        String prefix = dot >= 0 ? path.substring(0, dot) : path;
        return new File(prefix + extension);
    }

    private static void deleteIfExists(File file) {
        if (file.exists() && !file.delete()) {
            System.out.println("Warning: unable to delete existing file: " + file.getAbsolutePath());
        }
    }

    private static boolean shouldRemoveFeature(SimpleFeature feature, boolean showDetail,
            List<String> issueDetails, GeometryStats stats) {
        stats.total++;
        Object geometryObject = feature.getDefaultGeometry();

        if (!(geometryObject instanceof Geometry)) {
            stats.nullGeometry++;
            if (showDetail) {
                issueDetails.add(feature.getID() + ": geometry attribute is missing or not recognized");
            }
            return true;
        }

        Geometry geometry = (Geometry) geometryObject;
        if (geometry.isEmpty()) {
            stats.emptyGeometry++;
            if (showDetail) {
                issueDetails.add(feature.getID() + ": geometry is empty");
            }
            return true;
        }

        if (!geometry.isValid()) {
            stats.invalidGeometry++;
            IsValidOp validator = new IsValidOp(geometry);
            TopologyValidationError error = validator.getValidationError();
            String reason = error != null ? error.getMessage() : "Unknown validation error";

            Geometry repaired = GeometryFixer.fix(geometry);
            if (repaired != null && !repaired.isEmpty() && repaired.isValid()) {
                stats.fixableGeometry++;
            }

            if (showDetail) {
                StringBuilder detail = new StringBuilder();
                detail.append(feature.getID())
                        .append(": invalid geometry - ")
                        .append(reason);
                if (error != null && error.getCoordinate() != null) {
                    detail.append(" @ ")
                            .append(error.getCoordinate().x)
                            .append(",")
                            .append(error.getCoordinate().y);
                }
                issueDetails.add(detail.toString());
            }
            return true;
        }

        return false;
    }

    private static final class GeometryStats {
        int total;
        int nullGeometry;
        int emptyGeometry;
        int invalidGeometry;
        int fixableGeometry;
    }


    /**
     * 解析目标坐标系：支持EPSG:xxxx、纯数字EPSG、GeoTIFF自动识别。
     */
    private static CoordinateReferenceSystem resolveTargetCRS(String targetCRS) throws Exception {
        if (targetCRS == null || targetCRS.trim().isEmpty()) {
            throw new IllegalArgumentException("targetCRS is empty");
        }
        String trimmed = targetCRS.trim();
        String lower = trimmed.toLowerCase();

        // EPSG:xxxx 或纯数字
        if (lower.startsWith("epsg:")) {
            return CRS.decode(trimmed, true);
        }
        if (trimmed.matches("^\\d{3,6}$")) {
            return CRS.decode("EPSG:" + trimmed, true);
        }

        // GeoTIFF 自动解析
        if (lower.endsWith(".tif") || lower.endsWith(".tiff")) {
            File tif = new File(trimmed);
            if (!tif.exists()) {
                throw new IllegalArgumentException("GeoTIFF file not found: " + tif.getAbsolutePath());
            }
            GeoTiffReader reader = null;
            try {
                reader = new GeoTiffReader(tif);
                // 直接使用 getCoordinateReferenceSystem() 方法获取 CRS
                // 这样可以避免读取完整的图像数据，从而避免触发 ImageLayout 类的加载
                CoordinateReferenceSystem crs = reader.getCoordinateReferenceSystem();
                if (crs == null) {
                    throw new IllegalArgumentException("Unable to read CRS from GeoTIFF: " + tif.getAbsolutePath());
                }
                return crs;
            } finally {
                if (reader != null) {
                    reader.dispose();
                }
            }
        }

        // NetCDF 占位
        if (lower.endsWith(".nc")) {
            throw new UnsupportedOperationException("NetCDF support not implemented yet");
        }

        throw new IllegalArgumentException("Unsupported targetCRS: " + targetCRS);
    }

    /**
     * Escape special characters in CSV fields
     */
    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
    
    /**
     * 清理列名，移除或替换不适合作为列名的字符
     */
    private static String escapeCsvColumnName(String value) {
        if (value == null || value.isEmpty()) {
            return "null";
        }
        // 替换不适合作为列名的字符
        return value.replaceAll("[^a-zA-Z0-9_]", "_").replaceAll("_{2,}", "_");
    }
}
