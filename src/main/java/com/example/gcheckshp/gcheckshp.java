package com.example.gcheckshp;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.geotools.api.referencing.cs.CoordinateSystem;
import org.geotools.api.referencing.cs.CoordinateSystemAxis;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.data.DataUtilities;
import org.geotools.referencing.CRS;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.api.filter.FilterFactory;
import org.geotools.filter.FilterFactoryImpl;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Coordinate;

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
        
        if (args == null || args.length < 2) {
            printUsage();
            return;
        }
        
        // 优先检查 area 模式，如果检测到但方法不存在则明确报错
        // 这样可以确保使用旧版本 jar 时能立即发现并报错
        if (args.length >= 2 && "area".equalsIgnoreCase(args[1])) {
            try {
                handleAreaMode(args);
                return;
            } catch (NoSuchMethodError e) {
                // 如果 handleAreaMode 方法不存在（旧版本 jar），会抛出 NoSuchMethodError
                System.err.println("ERROR: This version of checkshp does not support 'area' mode.");
                System.err.println("The 'area' feature requires an updated version of the JAR file.");
                System.err.println("Please update to the latest version of checkshp-0.1.0.jar");
                System.exit(1);
            } catch (NoClassDefFoundError e) {
                // 如果相关类不存在，也会报错
                System.err.println("ERROR: This version of checkshp does not support 'area' mode.");
                System.err.println("The 'area' feature requires an updated version of the JAR file.");
                System.err.println("Please update to the latest version of checkshp-0.1.0.jar");
                System.exit(1);
            }
        }
        
        if (isIntersectionMode(args)) {
            handleIntersectionMode(args);
            return;
        }
        try {
            if (isAreaMode(args)) {
                handleAreaMode(args);
                return;
            }
        } catch (NoSuchMethodError e) {
            // 如果 isAreaMode 或 handleAreaMode 方法不存在（旧版本 jar），会抛出 NoSuchMethodError
            System.err.println("ERROR: This version of checkshp does not support 'area' mode.");
            System.err.println("The 'area' feature requires an updated version of the JAR file.");
            System.err.println("Please update to the latest version of checkshp-0.1.0.jar");
            System.exit(1);
        } catch (NoClassDefFoundError e) {
            // 如果相关类不存在，也会报错
            System.err.println("ERROR: This version of checkshp does not support 'area' mode.");
            System.err.println("The 'area' feature requires an updated version of the JAR file.");
            System.err.println("Please update to the latest version of checkshp-0.1.0.jar");
            System.exit(1);
        }
        if (args.length < 3) {
            printUsage();
            return;
        }
        handleCheckOrReprojectMode(args);
    }


    // Print usage in English
    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println(
                "  Check mode: java -jar gcheckshp-core.jar <shpPath> <detail|summary> <true|false> [targetCRS]");
        System.out.println(
                "  Reproject mode: java -jar gcheckshp-core.jar <shpPath> <detail|summary> <true|false> <targetCRS>");
        System.out.println(
                "  targetCRS can be: EPSG:xxxx, numeric EPSG code, .tif/.tiff file, or .shp file");
        System.out.println(
                "  Intersection stats: java -jar gcheckshp-core.jar <shp1> intersect <shp2> [--deduplicate-shp2] [--group-field <fieldName>]");
        System.out.println(
                "  Area calculation: java -jar gcheckshp-core.jar <shpPath> area [outputCSV]");
    }

    // Determine if intersection mode
    private static boolean isIntersectionMode(String[] args) {
        return args.length >= 3 && "intersect".equalsIgnoreCase(args[1]);
    }

    // Determine if area mode
    private static boolean isAreaMode(String[] args) {
        return args.length >= 2 && "area".equalsIgnoreCase(args[1]);
    }

    // Handle area calculation mode
    private static void handleAreaMode(String[] args) {
        String shpPath = args[0];
        String outputCSV = null;
        String projectionCRS = null;
        
        // 解析参数
        for (int i = 1; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--projection")) {
                if (i + 1 < args.length) {
                    projectionCRS = args[i + 1];
                    i++; // 跳过下一个参数
                } else {
                    System.out.println("Error: --projection requires a projection specification (EPSG code, TIF file, or SHP file)");
                    return;
                }
                continue;
            }
            // 跳过 "area" 固定参数
            if (args[i].equalsIgnoreCase("area")) {
                continue;
            }
            // 如果不是选项，可能是输出 CSV 文件路径
            if (outputCSV == null && !args[i].startsWith("--")) {
                outputCSV = args[i];
            }
        }
        
        // 检查投影参数是否提供（必选项）
        if (projectionCRS == null || projectionCRS.trim().isEmpty()) {
            System.out.println("Error: --projection is required for area calculation.");
            System.out.println("  Usage: --projection <EPSG_code|tif_file|shp_file>");
            System.out.println("  Examples:");
            System.out.println("    --projection EPSG:3857");
            System.out.println("    --projection reference.tif");
            System.out.println("    --projection reference.shp");
            return;
        }
        
        File shpFile = new File(shpPath);
        if (!shpFile.exists()) {
            System.out.println("Shapefile does not exist: " + shpPath);
            return;
        }
        if (!checkCompanionFiles(shpFile)) {
            return;
        }
        
        calculatePolygonAreas(shpPath, outputCSV, projectionCRS);
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
        boolean deduplicateOption = false;
        // clip功能现在默认启用，不再需要选项参数
        String groupField = null;
        String projectionCRS = null;
        
        // 首先找到shp2路径（第一个.shp后缀的参数）
        for (int i = 2; i < args.length; i++) {
            if (args[i].toLowerCase().endsWith(".shp")) {
                shp2 = args[i];
                break; // 找到第一个.shp文件后停止
            }
        }
        
        // 然后处理其他选项参数
        for (int i = 2; i < args.length; i++) {
            // --merge-shp2 选项现在调用去重功能（只merge重叠的要素）
            if (args[i].equalsIgnoreCase("--merge-shp2")) {
                deduplicateOption = true;
                continue;
            }
            if (args[i].equalsIgnoreCase("--deduplicate-shp2")) {
                deduplicateOption = true;
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
            if (args[i].equalsIgnoreCase("--projection")) {
                if (i + 1 < args.length) {
                    projectionCRS = args[i + 1];
                    i++; // 跳过下一个参数，因为它是投影参数
                } else {
                    System.out.println("Error: --projection requires a projection specification (EPSG code, TIF file, or SHP file)");
                    return;
                }
                continue;
            }
        }
        
        // 检查投影参数是否提供（必选项）
        if (projectionCRS == null || projectionCRS.trim().isEmpty()) {
            System.out.println("Error: --projection is required for intersection calculation.");
            System.out.println("  Usage: --projection <EPSG_code|tif_file|shp_file>");
            System.out.println("  Examples:");
            System.out.println("    --projection EPSG:3857");
            System.out.println("    --projection reference.tif");
            System.out.println("    --projection reference.shp");
            return;
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
        
        // clip功能现在默认启用，不再需要选项参数
        calculateIntersectionStats(shp1, shp2, groupField, deduplicateOption, true, projectionCRS);
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
            
            // 对于大文件，限制详细信息的数量以避免内存问题
            final int MAX_DETAIL_ITEMS = 10000;
            final int[] detailCount = {0}; // 使用数组以便在lambda中修改

            if (doDelete) {
                Transaction deleteTransaction = new DefaultTransaction("delete-invalid");
                try (FeatureWriter<SimpleFeatureType, SimpleFeature> writer = 
                        store.getFeatureWriter(store.getTypeNames()[0], deleteTransaction)) {
                    int processedCount = 0;
                    while (writer.hasNext()) {
                        SimpleFeature feature = writer.next();
                        boolean removeFeature = shouldRemoveFeature(feature, showDetail && detailCount[0] < MAX_DETAIL_ITEMS, 
                                issueDetails, stats, detailCount);
                        if (removeFeature) {
                            writer.remove();
                            deletedGeometries++;
                        } else {
                            writer.write();
                        }
                        processedCount++;
                        // 每处理1000个要素后提示垃圾回收
                        if (processedCount % 1000 == 0) {
                            System.gc();
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
                    int processedCount = 0;
                    while (iterator.hasNext()) {
                        SimpleFeature feature = iterator.next();
                        shouldRemoveFeature(feature, showDetail && detailCount[0] < MAX_DETAIL_ITEMS, issueDetails, stats, detailCount);
                        processedCount++;
                        // 每处理1000个要素后提示垃圾回收
                        if (processedCount % 1000 == 0) {
                            System.gc();
                        }
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
            boolean deduplicateShp2, boolean clipShp2ToShp1Bounds, String projectionCRS) {
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
            
            // 处理流程：
            // 1. 先将shp2转换到shp1的坐标系（如果不同）
            // 2. 在shp1的坐标系中进行clip
            // 3. 最后将两个shp都转换到用户指定的目标坐标系
            // 这样可以确保clip在相同的坐标系中进行，更准确，并且先clip再转换可以减少数据量，避免NaN坐标问题
            
            // 确定用于面积计算的坐标系（使用用户指定的投影）
            CoordinateReferenceSystem areaCalculationCRS = null;
            MathTransform transform1 = null;
            MathTransform transform2 = null;
            MathTransform shp2ToShp1Transform = null; // shp2到shp1的转换（用于clip）
            Geometry clipBoundaryInShp1CRS = null; // 在shp1坐标系中的clip边界
            String areaUnit = "unknown";
            
            // 解析用户指定的投影
            try {
                if (projectionCRS == null || projectionCRS.trim().isEmpty()) {
                    throw new IllegalArgumentException("Projection CRS is required. Please specify a projection using --projection option.");
                }
                
                // 使用用户指定的投影（EPSG、TIF、SHP）
                areaCalculationCRS = resolveTargetCRS(projectionCRS);
                
                // 步骤1：如果shp2和shp1的坐标系不同，创建shp2到shp1的转换（用于clip）
                boolean needShp2ToShp1Transform = false;
                if (crs1 != null && crs2 != null && !CRS.equalsIgnoreMetadata(crs1, crs2)) {
                    shp2ToShp1Transform = CRS.findMathTransform(crs2, crs1, true);
                    needShp2ToShp1Transform = true;
                }
                
                // 步骤2：在shp1的坐标系中进行clip
                if (clipShp2ToShp1Bounds) {
                    if (bounds1 == null || bounds1.isEmpty()) {
                    } else {
                        try {
                            // 创建裁剪边界（使用 shp1 的边界框，在shp1的坐标系中）
                            GeometryFactory gf = new GeometryFactory();
                            Envelope env = new Envelope(bounds1.getMinX(), bounds1.getMaxX(), 
                                                        bounds1.getMinY(), bounds1.getMaxY());
                            clipBoundaryInShp1CRS = gf.toGeometry(env);
                            
                            // 如果shp2的坐标系与shp1不同，需要将clip边界转换到shp2的坐标系进行Filter
                            // 但实际的clip会在shp1坐标系中进行（在处理几何对象时）
                            if (needShp2ToShp1Transform) {
                                // 将clip边界转换到shp2坐标系，用于Filter（初步筛选）
                                MathTransform clipBoundaryTransform = CRS.findMathTransform(crs1, crs2, true);
                                Geometry clipBoundaryInShp2CRS = org.geotools.geometry.jts.JTS.transform(clipBoundaryInShp1CRS, clipBoundaryTransform);
                                
                                FilterFactory filterFactory = new FilterFactoryImpl();
                                org.geotools.api.filter.Filter clipFilter = filterFactory.intersects(
                                    filterFactory.property(featureSource2.getSchema().getGeometryDescriptor().getLocalName()),
                                    filterFactory.literal(clipBoundaryInShp2CRS)
                                );
                                collection2 = featureSource2.getFeatures(clipFilter);
                            } else {
                                // 坐标系相同，直接使用shp1的边界进行clip
                                FilterFactory filterFactory = new FilterFactoryImpl();
                                org.geotools.api.filter.Filter clipFilter = filterFactory.intersects(
                                    filterFactory.property(featureSource2.getSchema().getGeometryDescriptor().getLocalName()),
                                    filterFactory.literal(clipBoundaryInShp1CRS)
                                );
                                collection2 = featureSource2.getFeatures(clipFilter);
                            }
                            
                            // 统计clip后的要素数量
                            int clippedCount = collection2.size();
                        } catch (Exception e) {
                            System.out.println("Warning: Failed to create clip filter, processing all shp2 features: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
                
                // 步骤3：创建转换到目标坐标系的转换
                // 注意：如果原始shapefile的CRS与目标CRS相同，则跳过转换
                // shp1: 从crs1转换到目标坐标系（如果CRS相同，则跳过转换）
                if (crs1 != null) {
                    if (CRS.equalsIgnoreMetadata(crs1, areaCalculationCRS)) {
                        transform1 = null; // CRS相同，跳过转换
                    } else {
                        transform1 = CRS.findMathTransform(crs1, areaCalculationCRS, true);
                    }
                }
                
                // shp2: 如果需要在shp1坐标系中clip，则从crs1转换到目标坐标系
                // 否则从crs2转换到目标坐标系
                // 注意：如果原始shapefile的CRS与目标CRS相同，则跳过转换
                if (clipShp2ToShp1Bounds && needShp2ToShp1Transform) {
                    // shp2会先转换到shp1坐标系（在clip时），然后从shp1坐标系转换到目标坐标系
                    // 所以transform2应该是从crs1到目标坐标系
                    if (crs1 != null) {
                        if (CRS.equalsIgnoreMetadata(crs1, areaCalculationCRS)) {
                            transform2 = null; // CRS相同，跳过转换
                        } else {
                            transform2 = CRS.findMathTransform(crs1, areaCalculationCRS, true);
                        }
                    }
                } else {
                    // 没有clip或坐标系相同，直接从原始坐标系转换到目标坐标系
                    // 如果原始shapefile的CRS与目标CRS相同，则跳过转换
                    if (crs2 != null) {
                        if (CRS.equalsIgnoreMetadata(crs2, areaCalculationCRS)) {
                            transform2 = null; // CRS相同，跳过转换
                        } else {
                            transform2 = CRS.findMathTransform(crs2, areaCalculationCRS, true);
                        }
                    }
                }
                
                
                // 确定面积单位
                try {
                    CoordinateSystem cs = areaCalculationCRS.getCoordinateSystem();
                    if (cs != null && cs.getDimension() > 0) {
                        CoordinateSystemAxis axis = cs.getAxis(0);
                        if (axis != null) {
                            String unit = axis.getUnit().toString();
                            String unitLower = unit.toLowerCase();
                            if (unitLower.contains("metre") || unitLower.contains("meter") || unitLower.equals("m")) {
                                areaUnit = "square meters";
                            } else if (unitLower.equals("ft") || unitLower.contains("foot") || unitLower.contains("feet")) {
                                areaUnit = "square feet";
                            } else if (unitLower.equals("km") || unitLower.contains("kilometre") || unitLower.contains("kilometer")) {
                                areaUnit = "square kilometers";
                            } else {
                                // 使用ASCII兼容格式，避免Unicode字符显示问题
                                areaUnit = "square " + unit;
                            }
                        }
                    }
                    if ("unknown".equals(areaUnit)) {
                        areaUnit = "square units";
                    }
                } catch (Exception e) {
                    areaUnit = "square units";
                }
            } catch (IllegalArgumentException e) {
                // 投影解析失败，直接输出错误信息并退出
                System.err.println("ERROR: " + e.getMessage());
                System.exit(1);
            } catch (Exception e) {
                System.err.println("ERROR: Failed to resolve projection: " + e.getMessage());
                throw new RuntimeException("Failed to resolve projection: " + e.getMessage(), e);
            }

            // Validate groupField existence
            if (groupField != null && !validateGroupField(store2, groupField)) {
                System.err.println("Error: Group field '" + groupField
                        + "' does not exist in shp2 attributes. Operation stopped.");
                logger.severe("Group field '" + groupField + "' does not exist in shp2 attributes.");
                return; // 停止执行
            }

            // 优化内存使用：根据是否需要合并采用不同策略
            // 如果不需要合并，使用流式索引（只存储envelope和feature引用）
            // 如果需要合并，使用分批合并策略
            
            // 首先统计 shp2 的要素数量
            int shp2GeomCount = collection2.size();
            int shp2PolygonCount = 0;
            if (shp2GeomCount == 0) {
                logger.severe("shp2 has no valid geometries.");
                return;
            }
            
            // 根据是否需要去重选择不同的处理策略
            org.locationtech.jts.index.strtree.STRtree strTree = null;
            Map<String, List<Geometry>> groupGeoms = null;
            Map<Geometry, String> geomToGroup = null;
            List<String> uniqueGroupValues = groupField != null ? new ArrayList<>() : null;
            
            // 不再需要全局merge，只使用去重功能（在构建索引时处理）
            // 使用流式索引策略，只存储envelope和feature引用
            // 如果启用去重，需要先收集所有几何对象，检测重叠并分组merge
            if (deduplicateShp2) {
                    List<Geometry> allGeometries = new ArrayList<>();
                    List<SimpleFeature> allFeatures = new ArrayList<>(); // 保存feature引用用于分组信息
                    
                    // 第一步：收集所有几何对象
                    try (SimpleFeatureIterator iterator2 = collection2.features()) {
                        while (iterator2.hasNext()) {
                            SimpleFeature feature = iterator2.next();
                            Object geomObj = feature.getDefaultGeometry();
                            if (geomObj instanceof Geometry) {
                                Geometry geom = (Geometry) geomObj;
                                if (!geom.isEmpty() && geom.isValid()) {
                                    // 步骤1：如果shp2和shp1坐标系不同，先转换到shp1坐标系
                                    if (shp2ToShp1Transform != null) {
                                        try {
                                            geom = org.geotools.geometry.jts.JTS.transform(geom, shp2ToShp1Transform);
                                            if (!geom.isValid()) {
                                                try {
                                                    Geometry fixed = GeometryFixer.fix(geom);
                                                    if (fixed.isValid()) {
                                                        geom = fixed;
                                                    } else {
                                                        continue;
                                                    }
                                                } catch (Exception fixEx) {
                                                    continue;
                                                }
                                            }
                                        } catch (Exception e) {
                                            continue;
                                        }
                                    }
                                    
                                    // 步骤2：在shp1坐标系中进行clip（如果启用）
                                    if (clipShp2ToShp1Bounds && clipBoundaryInShp1CRS != null) {
                                        try {
                                            if (!geom.intersects(clipBoundaryInShp1CRS)) {
                                                continue;
                                            }
                                        } catch (Exception e) {
                                            continue;
                                        }
                                    }
                                    
                                    // 步骤3：转换到目标坐标系
                                    if (transform2 != null) {
                                        try {
                                            geom = org.geotools.geometry.jts.JTS.transform(geom, transform2);
                                            if (!geom.isValid()) {
                                                try {
                                                    Geometry fixed = GeometryFixer.fix(geom);
                                                    if (fixed.isValid()) {
                                                        geom = fixed;
                                                    } else {
                                                        continue;
                                                    }
                                                } catch (Exception fixEx) {
                                                    continue;
                                                }
                                            }
                                        } catch (Exception e) {
                                            continue;
                                        }
                                    } else {
                                        if (!geom.isValid()) {
                                            try {
                                                Geometry fixed = GeometryFixer.fix(geom);
                                                if (fixed.isValid()) {
                                                    geom = fixed;
                                                } else {
                                                    continue;
                                                }
                                            } catch (Exception fixEx) {
                                                continue;
                                            }
                                        }
                                    }
                                    
                                    String geomType = geom.getGeometryType();
                                    if ("Polygon".equalsIgnoreCase(geomType) || "MultiPolygon".equalsIgnoreCase(geomType)) {
                                        allGeometries.add(geom);
                                        allFeatures.add(feature);
                                    }
                                }
                            }
                        }
                    }
                    
                    if (allGeometries.isEmpty()) {
                        logger.severe("shp2 contains no Polygon/MultiPolygon geometries.");
                        return;
                    }
                    
                    // 第二步：检测重叠并分组（使用并查集）
                    List<List<Integer>> overlapGroups = findOverlapGroups(allGeometries);
                    
                    // 第三步：对每组进行merge，并构建最终索引
                    strTree = new org.locationtech.jts.index.strtree.STRtree(STRTREE_NODE_CAPACITY);
                    int mergedGroupCount = 0;
                    int nonOverlappingCount = 0;
                    
                    if (groupField != null) {
                        groupGeoms = new HashMap<>();
                        geomToGroup = new HashMap<>();
                    }
                    
                    // 标记哪些几何对象已经被分组
                    boolean[] inGroup = new boolean[allGeometries.size()];
                    
                    for (List<Integer> group : overlapGroups) {
                        if (group.size() > 1) {
                            // 多个几何对象重叠，需要merge
                            List<Geometry> groupGeomList = new ArrayList<>();
                            for (int idx : group) {
                                groupGeomList.add(allGeometries.get(idx));
                                inGroup[idx] = true;
                            }
                            
                            try {
                                Geometry merged = mergeGeometriesRobustly(groupGeomList, null, 0, 0);
                                if (merged != null && !merged.isEmpty() && merged.isValid()) {
                                    strTree.insert(merged.getEnvelopeInternal(), merged);
                                    shp2PolygonCount++;
                                    mergedGroupCount++;
                                    
                                    // 记录分组信息（使用第一个feature的分组信息）
                                    if (groupField != null && allFeatures.size() > group.get(0)) {
                                        SimpleFeature firstFeature = allFeatures.get(group.get(0));
                                        Object attr = firstFeature.getAttribute(groupField);
                                        String key = (attr != null) ? attr.toString() : "<null>";
                                        if (groupGeoms == null) {
                                            groupGeoms = new HashMap<>();
                                            geomToGroup = new HashMap<>();
                                        }
                                        final List<String> finalUniqueGroupValues = uniqueGroupValues;
                                        groupGeoms.computeIfAbsent(key, k -> {
                                            if (finalUniqueGroupValues == null) {
                                                // 使用反射或外部变量来设置uniqueGroupValues
                                            } else {
                                                finalUniqueGroupValues.add(key);
                                            }
                                            return new ArrayList<>();
                                        }).add(merged);
                                        if (uniqueGroupValues == null) {
                                            uniqueGroupValues = new ArrayList<>();
                                        }
                                        if (!uniqueGroupValues.contains(key)) {
                                            uniqueGroupValues.add(key);
                                        }
                                        if (geomToGroup != null) {
                                            geomToGroup.put(merged, key);
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                logger.warning("Failed to merge overlapping group: " + e.getMessage());
                                // merge失败，保留原始几何对象
                                for (int idx : group) {
                                    Geometry geom = allGeometries.get(idx);
                                    strTree.insert(geom.getEnvelopeInternal(), geom);
                                    shp2PolygonCount++;
                                }
                            }
                        }
                    }
                    
                    // 添加不重叠的几何对象
                    for (int i = 0; i < allGeometries.size(); i++) {
                        if (!inGroup[i]) {
                            Geometry geom = allGeometries.get(i);
                            strTree.insert(geom.getEnvelopeInternal(), geom);
                            shp2PolygonCount++;
                            nonOverlappingCount++;
                            
                            // 记录分组信息
                            if (groupField != null && allFeatures.size() > i) {
                                SimpleFeature feature = allFeatures.get(i);
                                Object attr = feature.getAttribute(groupField);
                                String key = (attr != null) ? attr.toString() : "<null>";
                                if (groupGeoms == null) {
                                    groupGeoms = new HashMap<>();
                                    geomToGroup = new HashMap<>();
                                }
                                groupGeoms.computeIfAbsent(key, k -> new ArrayList<>()).add(geom);
                                if (uniqueGroupValues == null) {
                                    uniqueGroupValues = new ArrayList<>();
                                }
                                if (!uniqueGroupValues.contains(key)) {
                                    uniqueGroupValues.add(key);
                                }
                                if (geomToGroup != null) {
                                    geomToGroup.put(geom, key);
                                }
                            }
                        }
                    }
                    
                    
                } else {
                    // 不使用去重：使用原来的流式处理
                    strTree = new org.locationtech.jts.index.strtree.STRtree(STRTREE_NODE_CAPACITY);
                    List<Geometry> tempGeoms = new ArrayList<>();
                    final int INDEX_BATCH_SIZE = 5000;
                    
                    if (groupField != null) {
                        groupGeoms = new HashMap<>();
                        geomToGroup = new HashMap<>();
                    }
                
                try (SimpleFeatureIterator iterator2 = collection2.features()) {
                    while (iterator2.hasNext()) {
                        SimpleFeature feature = iterator2.next();
                        Object geomObj = feature.getDefaultGeometry();
                        if (geomObj instanceof Geometry) {
                            Geometry geom = (Geometry) geomObj;
                            if (!geom.isEmpty() && geom.isValid()) {
                                // 步骤1：如果shp2和shp1坐标系不同，先转换到shp1坐标系
                                if (shp2ToShp1Transform != null) {
                                    try {
                                        geom = org.geotools.geometry.jts.JTS.transform(geom, shp2ToShp1Transform);
                                        // 只检查几何对象的有效性，不需要检查坐标
                                        if (!geom.isValid()) {
                                            try {
                                                Geometry fixed = GeometryFixer.fix(geom);
                                                if (fixed.isValid()) {
                                                    geom = fixed;
                                                } else {
                                                    logger.warning("Skipping invalid geometry from shp2 after conversion to shp1 CRS (cannot be fixed)");
                                                    continue;
                                                }
                                            } catch (Exception fixEx) {
                                                logger.warning("Skipping invalid geometry from shp2 after conversion to shp1 CRS (fix failed)");
                                                continue;
                                            }
                                        }
                                    } catch (Exception e) {
                                        logger.warning("Failed to transform geometry from shp2 to shp1 CRS: " + e.getMessage());
                                        continue;
                                    }
                                }
                                
                                // 步骤2：在shp1坐标系中进行clip（如果启用）
                                if (clipShp2ToShp1Bounds && clipBoundaryInShp1CRS != null) {
                                    try {
                                        if (!geom.intersects(clipBoundaryInShp1CRS)) {
                                            // 几何对象不在clip边界内，跳过
                                            continue;
                                        }
                                    } catch (Exception e) {
                                        logger.warning("Failed to clip geometry: " + e.getMessage());
                                        continue;
                                    }
                                }
                                
                                // 步骤3：转换到目标坐标系
                                if (transform2 != null) {
                                    try {
                                        // 转换前检查几何对象的范围
                                        Envelope envBefore = geom.getEnvelopeInternal();
                                        if (envBefore != null) {
                                            double width = envBefore.getWidth();
                                            double height = envBefore.getHeight();
                                            // 如果几何对象范围过大，可能转换时会产生NaN
                                            if (width > 1000000 || height > 1000000) {
                                                logger.warning("Large geometry detected before transformation (width: " + width + ", height: " + height + "). This may cause NaN coordinates.");
                                            }
                                        }
                                        
                                        geom = org.geotools.geometry.jts.JTS.transform(geom, transform2);
                                        
                                        // 只检查几何对象的有效性，不需要检查坐标
                                        if (!geom.isValid()) {
                                            try {
                                                Geometry fixed = GeometryFixer.fix(geom);
                                                if (fixed.isValid()) {
                                                    geom = fixed;
                                                    logger.warning("Fixed invalid geometry after transformation from shp2 to target CRS");
                                                } else {
                                                    Envelope envAfter = geom.getEnvelopeInternal();
                                                    logger.warning("Skipping invalid geometry from shp2 after transformation to target CRS (cannot be fixed). " +
                                                                  "Before: " + envBefore + ", After: " + envAfter + 
                                                                  ". This may be caused by large data range or inappropriate projection.");
                                                    continue;
                                                }
                                            } catch (Exception fixEx) {
                                                Envelope envAfter = geom.getEnvelopeInternal();
                                                logger.warning("Skipping invalid geometry from shp2 after transformation to target CRS (fix failed). " +
                                                              "Before: " + envBefore + ", After: " + envAfter);
                                                continue;
                                            }
                                        }
                                    } catch (Exception e) {
                                        // 检查是否是转换相关的异常
                                        if (e.getMessage() != null && (e.getMessage().contains("Transform") || e.getMessage().contains("transform"))) {
                                            logger.warning("Transform error when transforming geometry from shp2 to target CRS: " + e.getMessage() + 
                                                          ". This may indicate the projection is not suitable for this data range.");
                                        } else {
                                            logger.warning("Failed to transform geometry from shp2 to target CRS: " + e.getMessage());
                                        }
                                        continue;
                                    }
                                } else {
                                    // 即使没有转换，也验证几何对象的有效性
                                    if (!geom.isValid()) {
                                        try {
                                            Geometry fixed = GeometryFixer.fix(geom);
                                            if (fixed.isValid()) {
                                                geom = fixed;
                                            } else {
                                                logger.warning("Skipping invalid geometry from shp2 (cannot be fixed)");
                                                continue;
                                            }
                                        } catch (Exception fixEx) {
                                            logger.warning("Skipping invalid geometry from shp2 (fix failed)");
                                            continue;
                                        }
                                    }
                                }
                                String geomType = geom.getGeometryType();
                                if ("Polygon".equalsIgnoreCase(geomType) || "MultiPolygon".equalsIgnoreCase(geomType)) {
                                    strTree.insert(geom.getEnvelopeInternal(), geom);
                                    tempGeoms.add(geom);
                                    shp2PolygonCount++;
                                    
                                    // 记录分组信息
                                    if (groupField != null && groupGeoms != null && geomToGroup != null && uniqueGroupValues != null) {
                                        Object attr = feature.getAttribute(groupField);
                                        String key = (attr != null) ? attr.toString() : "<null>";
                                        groupGeoms.computeIfAbsent(key, k -> new ArrayList<>()).add(geom);
                                        if (uniqueGroupValues == null) {
                                            uniqueGroupValues = new ArrayList<>();
                                        }
                                        if (!uniqueGroupValues.contains(key)) {
                                            uniqueGroupValues.add(key);
                                        }
                                        geomToGroup.put(geom, key);
                                    }
                                    
                                    // 定期清理临时列表以释放内存（索引已构建，可以清理）
                                    if (tempGeoms.size() >= INDEX_BATCH_SIZE) {
                                        tempGeoms.clear();
                                        System.gc(); // 提示垃圾回收
                                    }
                                }
                            }
                        }
                    }
                }
                
                if (shp2PolygonCount == 0) {
                    logger.severe("shp2 contains no Polygon/MultiPolygon geometries.");
                    return;
                }
                
                tempGeoms.clear();
                System.gc(); // 最终清理
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
            // 只使用索引模式（去重功能在构建索引时已处理）
            totalIntersectionArea = computeIntersectionsWithAttributesAndWrite(collection1, transform1, 
                    shp1FieldNames, strTree, csvFile, groupField, 
                    groupField != null ? geomToGroup : null,
                    uniqueGroupValues);

            // 输出统计结果到控制台
            System.out.println("--- Area Calculation Settings ---");
            System.out.println("Coordinate System (CRS): "
                    + (areaCalculationCRS != null ? areaCalculationCRS.getName().toString() : "unknown"));
            System.out.println("Area Unit: " + areaUnit);
            System.out.println("Total intersection area: " + String.format("%.6f", totalIntersectionArea) + " " + areaUnit);
            System.out.println("CSV file saved to: " + csvFile.getAbsolutePath());
        } catch (org.locationtech.jts.geom.TopologyException e) {
            System.err.println("Failed to calculate intersection statistics: " + e.getMessage());
            e.printStackTrace();
        } catch (RuntimeException e) {
            System.err.println("Failed to calculate intersection statistics: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Failed to calculate intersection statistics: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (store1 != null)
                store1.dispose();
            if (store2 != null)
                store2.dispose();
        }
    }

    // 构建合并失败的错误消息，包含原因和建议
    private static String buildMergeErrorMessage(String target, String reason) {
        StringBuilder msg = new StringBuilder();
        msg.append("ERROR: Failed to merge ").append(target).append(".\n");
        msg.append("原因 (Reason): ").append(reason).append("\n");
        msg.append("可能的原因包括：\n");
        msg.append("  1. shp2 中包含无效的几何对象（包含 NaN 坐标或拓扑错误）\n");
        msg.append("  2. 几何对象数量过多，合并时出现内存或拓扑冲突\n");
        msg.append("  3. 坐标转换后产生了无效的几何对象\n");
        msg.append("\n");
        msg.append("建议的解决方案 (Suggested Solutions):\n");
        msg.append("  1. 不使用 merge 选项：直接使用空间索引模式（默认模式）\n");
        msg.append("     命令示例: intershp shp1 with(shp2)\n");
        msg.append("     注意：不使用 merge 选项时，计算速度可能较慢，但更稳定\n");
        msg.append("  2. 先检查和修复 shp2：使用 checkshp 命令检查并修复 shp2 中的无效几何对象\n");
        msg.append("     命令示例: checkshp shp2 detail clean\n");
        msg.append("  3. 尝试使用不同的投影坐标系：某些投影可能更适合您的数据范围\n");
        return msg.toString();
    }
    
    // 分批合并几何对象，减少内存峰值
    private static Geometry mergeGeometriesInBatches(List<Geometry> geometries, int batchSize) {
        return mergeGeometriesInBatches(geometries, batchSize, null, 0);
    }
    
    // 带进度显示的分批合并
    private static Geometry mergeGeometriesInBatches(List<Geometry> geometries, int batchSize, String progressPrefix, int totalBatches) {
        if (geometries == null || geometries.isEmpty()) {
            return null;
        }
        
        // 首先过滤掉无效的几何对象（只检查几何有效性，不检查坐标）
        List<Geometry> validGeometries = new ArrayList<>();
        for (Geometry geom : geometries) {
            if (geom != null && !geom.isEmpty() && geom.isValid()) {
                validGeometries.add(geom);
            } else if (geom != null && !geom.isEmpty()) {
                // 尝试修复
                try {
                    Geometry fixed = GeometryFixer.fix(geom);
                    if (fixed != null && !fixed.isEmpty() && fixed.isValid()) {
                        validGeometries.add(fixed);
                    } else {
                        logger.warning("Skipping invalid geometry in merge (cannot be fixed)");
                    }
                } catch (Exception e) {
                    logger.warning("Skipping invalid geometry in merge (fix failed)");
                }
            }
        }
        
        if (validGeometries.isEmpty()) {
            return null;
        }
        
        if (validGeometries.size() == 1) {
            return validGeometries.get(0);
        }
        
        // 如果几何对象数量较少，使用逐个合并策略，跳过有问题的几何对象
        if (validGeometries.size() <= batchSize) {
            return mergeGeometriesRobustly(validGeometries, progressPrefix, 0, 0);
        }
        
        // 分批合并，每批使用健壮的合并策略
        List<Geometry> mergedBatches = new ArrayList<>();
        int actualTotalBatches = (validGeometries.size() + batchSize - 1) / batchSize;
        if (totalBatches == 0) totalBatches = actualTotalBatches;
        
        for (int i = 0; i < validGeometries.size(); i += batchSize) {
            int end = Math.min(i + batchSize, validGeometries.size());
            List<Geometry> batch = new ArrayList<>(validGeometries.subList(i, end));
            int batchNum = i / batchSize + 1;
            if (progressPrefix != null) {
                showProgress(batchNum, totalBatches, progressPrefix);
            }
            Geometry batchMerged = mergeGeometriesRobustly(batch, null, 0, 0);
            // 在每个批次合并后立即验证（只检查几何有效性）
            if (batchMerged != null && !batchMerged.isEmpty()) {
                if (batchMerged.isValid()) {
                    mergedBatches.add(batchMerged);
                } else {
                    // 尝试修复
                    try {
                        Geometry fixed = GeometryFixer.fix(batchMerged);
                        if (fixed != null && !fixed.isEmpty() && fixed.isValid()) {
                            mergedBatches.add(fixed);
                        } else {
                            logger.warning("Skipping invalid merged batch " + (i / batchSize + 1));
                        }
                    } catch (Exception e) {
                        logger.warning("Skipping invalid merged batch " + (i / batchSize + 1) + " (fix failed)");
                    }
                }
            }
            
            // 每处理几个批次后提示垃圾回收
            if ((i / batchSize) % 10 == 0 && i > 0) {
                System.gc();
            }
        }
        
        // 如果合并后只有一个批次，直接返回
        if (mergedBatches.size() == 1) {
            return mergedBatches.get(0);
        }
        
        if (mergedBatches.isEmpty()) {
            return null;
        }
        
        // 递归合并合并后的批次
        return mergeGeometriesInBatches(mergedBatches, batchSize, progressPrefix, 0);
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
            
            int processedCount = 0;
            int skippedCount = 0;
            int writtenCount = 0;
            
            while (iterator1.hasNext()) {
                SimpleFeature feature = iterator1.next();
                Object geomObj = feature.getDefaultGeometry();
                if (geomObj instanceof Geometry) {
                    Geometry geom = (Geometry) geomObj;
                    if (!geom.isEmpty() && geom.isValid()) {
                        geom = transformGeometry(geom, transform1, feature.getID());
                        if (geom == null) {
                            skippedCount++;
                            continue;
                        }
                        
                        processedCount++;
                        double featureArea = geom.getArea();
                        Envelope geomEnv = geom.getEnvelopeInternal();
                        IntersectionResult result = calculateIntersections(geom, geomEnv, strTree, 
                                feature.getID(), groupField, geomToGroup);
                        
                        totalIntersectionArea += result.area;
                        writeCsvRow(writer, feature, shp1FieldNames, featureArea, result.area, 
                                result.count, groupField, result.groupStats, uniqueGroupValues);
                        writer.flush(); // 确保数据立即写入
                        writtenCount++;
                    } else {
                        skippedCount++;
                    }
                } else {
                    skippedCount++;
                }
            }
            
        } catch (IOException e) {
            logger.warning("Failed to write CSV file: " + csvFile.getAbsolutePath() + " - " + e.getMessage());
            e.printStackTrace();
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
    
    // 显示进度条
    private static void showProgress(int current, int total, String prefix) {
        // 禁用进度条显示，保持控制台输出简洁
        return;
    }
    
    // 转换几何坐标系
    private static Geometry transformGeometry(Geometry geom, MathTransform transform, String featureId) {
        if (transform != null) {
            try {
                Geometry transformed = org.geotools.geometry.jts.JTS.transform(geom, transform);
                // 只检查几何对象的有效性，不需要检查坐标
                if (!transformed.isValid()) {
                    // 尝试修复几何对象
                    try {
                        Geometry fixed = GeometryFixer.fix(transformed);
                        if (fixed.isValid()) {
                            logger.warning("Fixed invalid geometry after transformation (feature " + featureId + ")");
                            return fixed;
                        } else {
                            logger.warning("Skipping invalid geometry after transformation (feature " + featureId + ", cannot be fixed)");
                            return null;
                        }
                    } catch (Exception fixEx) {
                        logger.warning("Skipping invalid geometry after transformation (feature " + featureId + ", fix failed)");
                        return null;
                    }
                }
                return transformed;
            } catch (Exception e) {
                logger.warning("Failed to transform geometry from shp1 (feature " + featureId + "): " + e.getMessage());
                return null;
            }
        }
        return geom;
    }
    
    // 健壮的合并策略：逐个合并几何对象，跳过有问题的几何对象
    // 使用空间排序和UnaryUnionOp优化性能
    private static Geometry mergeGeometriesRobustly(List<Geometry> geometries) {
        return mergeGeometriesRobustly(geometries, null, 0, 0);
    }
    
    // 带进度显示的合并方法
    private static Geometry mergeGeometriesRobustly(List<Geometry> geometries, String progressPrefix, int currentBatch, int totalBatches) {
        if (geometries == null || geometries.isEmpty()) {
            return null;
        }
        if (geometries.size() == 1) {
            return geometries.get(0);
        }
        
        // 显示进度
        if (progressPrefix != null && totalBatches > 0) {
            showProgress(currentBatch, totalBatches, progressPrefix);
        }
        
        // 对于大量几何对象，先进行空间排序，然后使用空间聚类策略
        final int LARGE_BATCH_SIZE = 100; // 超过100个几何对象时使用空间聚类
        if (geometries.size() > LARGE_BATCH_SIZE) {
            // 空间排序：按envelope中心点排序，使相邻几何对象优先合并
            geometries.sort((g1, g2) -> {
                Envelope env1 = g1.getEnvelopeInternal();
                Envelope env2 = g2.getEnvelopeInternal();
                double centerX1 = (env1.getMinX() + env1.getMaxX()) / 2;
                double centerY1 = (env1.getMinY() + env1.getMaxY()) / 2;
                double centerX2 = (env2.getMinX() + env2.getMaxX()) / 2;
                double centerY2 = (env2.getMinY() + env2.getMaxY()) / 2;
                // 先按X排序，再按Y排序
                int cmp = Double.compare(centerX1, centerX2);
                if (cmp != 0) return cmp;
                return Double.compare(centerY1, centerY2);
            });
            
            // 使用中等批次大小进行分批合并
            final int MEDIUM_BATCH_SIZE = 50;
            List<Geometry> mergedBatches = new ArrayList<>();
            int numBatches = (geometries.size() + MEDIUM_BATCH_SIZE - 1) / MEDIUM_BATCH_SIZE;
            for (int i = 0; i < geometries.size(); i += MEDIUM_BATCH_SIZE) {
                int end = Math.min(i + MEDIUM_BATCH_SIZE, geometries.size());
                List<Geometry> batch = new ArrayList<>(geometries.subList(i, end));
                int batchNum = i / MEDIUM_BATCH_SIZE + 1;
                String batchPrefix = progressPrefix != null ? progressPrefix + " (batch " + batchNum + "/" + numBatches + ")" : null;
                Geometry batchResult = mergeGeometriesRobustly(batch, batchPrefix, batchNum, numBatches);
                // 在每个批次合并后立即验证（只检查几何有效性）
                if (batchResult != null && !batchResult.isEmpty() && batchResult.isValid()) {
                    mergedBatches.add(batchResult);
                }
            }
            if (mergedBatches.isEmpty()) {
                return null;
            }
            if (mergedBatches.size() == 1) {
                return mergedBatches.get(0);
            }
            // 递归合并批次
            return mergeGeometriesRobustly(mergedBatches, progressPrefix, 0, 0);
        }
        
        // 在合并前，先修复所有几何对象（只检查几何有效性）
        List<Geometry> fixedGeometries = new ArrayList<>();
        for (Geometry geom : geometries) {
            if (geom != null && !geom.isEmpty() && geom.isValid()) {
                fixedGeometries.add(geom);
            } else if (geom != null && !geom.isEmpty()) {
                // 尝试修复
                try {
                    Geometry fixed = GeometryFixer.fix(geom);
                    if (fixed != null && !fixed.isEmpty() && fixed.isValid()) {
                        fixedGeometries.add(fixed);
                    } else {
                        logger.warning("Skipping geometry that cannot be fixed");
                    }
                } catch (Exception e) {
                    logger.warning("Skipping geometry that causes fix exception: " + e.getMessage());
                }
            }
        }
        
        if (fixedGeometries.isEmpty()) {
            return null;
        }
        if (fixedGeometries.size() == 1) {
            return fixedGeometries.get(0);
        }
        
        // 直接使用UnaryUnionOp进行合并（最高效的方法）
        // 对于所有大小的批次都优先使用UnaryUnionOp，只有在失败时才回退
        Geometry result = null;
        int skippedCount = 0;
        
        // 优先使用UnaryUnionOp一次性合并所有几何对象（比逐个union快得多）
        // 对于所有大小的批次都尝试使用UnaryUnionOp
        try {
            org.locationtech.jts.operation.union.UnaryUnionOp unaryUnion = 
                new org.locationtech.jts.operation.union.UnaryUnionOp(fixedGeometries);
            result = unaryUnion.union();
            
            // 验证合并结果
            if (result == null || result.isEmpty() || !result.isValid()) {
                try {
                    result = GeometryFixer.fix(result);
                    if (result == null || result.isEmpty() || !result.isValid()) {
                        throw new RuntimeException("UnaryUnion result is invalid and cannot be fixed");
                    }
                } catch (Exception e) {
                    throw new RuntimeException("UnaryUnion result is invalid and fix failed: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            // 如果UnaryUnion失败，回退到分批合并策略
            logger.warning("UnaryUnion failed, falling back to incremental merge: " + e.getMessage());
            
            // 对于小批次（≤5个），使用逐个合并
            if (fixedGeometries.size() <= 5) {
                for (Geometry geom : fixedGeometries) {
                    if (result == null) {
                        // 验证第一个几何对象（只检查几何有效性）
                        if (geom == null || geom.isEmpty() || !geom.isValid()) {
                            try {
                                geom = GeometryFixer.fix(geom);
                                if (geom == null || geom.isEmpty() || !geom.isValid()) {
                                    result = null;
                                    skippedCount++;
                                    continue;
                                }
                            } catch (Exception fixEx) {
                                skippedCount++;
                                continue;
                            }
                        }
                        result = geom;
                    } else {
                        try {
                            // 在合并前验证（只检查几何有效性）
                            if (result == null || result.isEmpty() || !result.isValid()) {
                                result = GeometryFixer.fix(result);
                                if (result == null || result.isEmpty() || !result.isValid()) {
                                    throw new RuntimeException("Previous merge result is invalid and cannot be fixed");
                                }
                            }
                            
                            if (geom == null || geom.isEmpty() || !geom.isValid()) {
                                geom = GeometryFixer.fix(geom);
                                if (geom == null || geom.isEmpty() || !geom.isValid()) {
                                    skippedCount++;
                                    continue;
                                }
                            }
                            
                            // 尝试合并 - 使用 mergeSmallBatch 以确保一致的错误处理
                            Geometry merged = mergeSmallBatch(Arrays.asList(result, geom));
                            result = merged;
                        } catch (org.locationtech.jts.geom.TopologyException topoEx) {
                            throw new RuntimeException("Topology exception during merge: " + topoEx.getMessage(), topoEx);
                        } catch (RuntimeException rtEx) {
                            throw rtEx;
                        } catch (Exception mergeEx) {
                            throw new RuntimeException("Exception during merge: " + mergeEx.getMessage(), mergeEx);
                        }
                    }
                }
            } else {
                // 对于较大的批次（>5个），使用分批合并策略
                // 将几何对象分成更小的批次，然后递归合并
                final int FALLBACK_BATCH_SIZE = 20;
                List<Geometry> mergedBatches = new ArrayList<>();
                for (int i = 0; i < fixedGeometries.size(); i += FALLBACK_BATCH_SIZE) {
                    int end = Math.min(i + FALLBACK_BATCH_SIZE, fixedGeometries.size());
                    List<Geometry> batch = new ArrayList<>(fixedGeometries.subList(i, end));
                    Geometry batchResult = mergeGeometriesRobustly(batch);
                    if (batchResult != null && !batchResult.isEmpty() && batchResult.isValid()) {
                        mergedBatches.add(batchResult);
                    }
                }
                if (mergedBatches.isEmpty()) {
                    return null;
                }
                if (mergedBatches.size() == 1) {
                    result = mergedBatches.get(0);
                } else {
                    // 递归合并批次
                    result = mergeGeometriesRobustly(mergedBatches, progressPrefix, 0, 0);
                }
            }
        }
        
        if (skippedCount > 0) {
            System.out.println("Info: Skipped " + skippedCount + " invalid/problematic geometries during merge (out of " + fixedGeometries.size() + " total)");
        }
        
        return result;
    }
    
    // 合并小批次几何对象（最多2个），使用最安全的方法
    private static Geometry mergeSmallBatch(List<Geometry> batch) {
        if (batch == null || batch.isEmpty()) {
            return null;
        }
        if (batch.size() == 1) {
            return batch.get(0);
        }
        
        // 对于2个几何对象的情况，使用最安全的方法
        Geometry geom1 = batch.get(0);
        Geometry geom2 = batch.size() > 1 ? batch.get(1) : null;
        
        // 验证和修复第一个几何对象（只检查几何有效性）
        if (geom1 == null || geom1.isEmpty() || !geom1.isValid()) {
            try {
                geom1 = GeometryFixer.fix(geom1);
                if (geom1 == null || geom1.isEmpty() || !geom1.isValid()) {
                    return geom2 != null && !geom2.isEmpty() && geom2.isValid() ? geom2 : null;
                }
            } catch (Exception e) {
                return geom2 != null && !geom2.isEmpty() && geom2.isValid() ? geom2 : null;
            }
        }
        
        if (geom2 == null) {
            return geom1;
        }
        
        // 验证和修复第二个几何对象（只检查几何有效性）
        if (geom2 == null || geom2.isEmpty() || !geom2.isValid()) {
            try {
                geom2 = GeometryFixer.fix(geom2);
                if (geom2 == null || geom2.isEmpty() || !geom2.isValid()) {
                    return geom1;
                }
            } catch (Exception e) {
                return geom1;
            }
        }
        
        // 尝试合并两个几何对象
        try {
            Geometry merged = geom1.union(geom2);
            
            // 立即验证合并结果（只检查几何有效性）
            if (merged == null || merged.isEmpty() || !merged.isValid()) {
                // 尝试修复
                merged = GeometryFixer.fix(merged);
                if (merged == null || merged.isEmpty() || !merged.isValid()) {
                    // 如果修复失败，抛出异常而不是返回可能无效的几何对象
                    throw new RuntimeException("Merge failed: result is invalid after fix attempt");
                }
            }
            
            return merged;
        } catch (org.locationtech.jts.geom.TopologyException e) {
            // 拓扑异常，抛出运行时异常而不是返回可能无效的几何对象
            throw new RuntimeException("Topology exception during merge: " + e.getMessage() + 
                " (coordinates may contain NaN or geometry is invalid)", e);
        } catch (RuntimeException e) {
            // 重新抛出运行时异常
            throw e;
        } catch (Exception e) {
            // 其他异常，抛出运行时异常
            throw new RuntimeException("Exception during merge: " + e.getMessage(), e);
        }
    }
    
    // 验证几何对象是否有效（检查 NaN 坐标和有效性，包括 z 坐标）
    private static boolean isValidGeometry(Geometry geom) {
        if (geom == null || geom.isEmpty()) {
            return false;
        }
        
        // 检查坐标中是否有 NaN（包括 x, y, z 坐标）
        try {
            org.locationtech.jts.geom.Coordinate[] coords = geom.getCoordinates();
            for (org.locationtech.jts.geom.Coordinate coord : coords) {
                if (Double.isNaN(coord.x) || Double.isNaN(coord.y) || 
                    Double.isInfinite(coord.x) || Double.isInfinite(coord.y)) {
                    return false;
                }
                // 检查 z 坐标（如果存在）
                double z = coord.getZ();
                if (Double.isNaN(z) || Double.isInfinite(z)) {
                    return false;
                }
            }
        } catch (Exception e) {
            return false;
        }
        
        // 检查几何对象是否有效
        try {
            if (!geom.isValid()) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        
        return true;
    }
    
    // 宽松的验证函数：只检查NaN/Infinity坐标，不检查拓扑有效性
    // 用于转换后的几何对象验证，因为转换可能产生轻微的拓扑问题，但坐标本身是正常的
    private static boolean hasValidCoordinates(Geometry geom) {
        if (geom == null || geom.isEmpty()) {
            return false;
        }
        
        // 只检查坐标中是否有 NaN（包括 x, y, z 坐标）
        try {
            org.locationtech.jts.geom.Coordinate[] coords = geom.getCoordinates();
            if (coords == null || coords.length == 0) {
                return false;
            }
            for (org.locationtech.jts.geom.Coordinate coord : coords) {
                if (coord == null) {
                    return false;
                }
                if (Double.isNaN(coord.x) || Double.isNaN(coord.y) || 
                    Double.isInfinite(coord.x) || Double.isInfinite(coord.y)) {
                    return false;
                }
                // 检查 z 坐标（如果存在）
                // 注意：对于2D几何，z坐标可能是NaN（默认值），这是正常的，不应该导致验证失败
                // 只有当z坐标是Infinity时才认为无效
                try {
                    double z = coord.getZ();
                    // 只检查Infinity，不检查NaN（因为2D几何的z坐标默认是NaN）
                    if (Double.isInfinite(z)) {
                        return false;
                    }
                } catch (Exception e) {
                    // z坐标获取失败，但x和y有效，继续
                }
            }
        } catch (Exception e) {
            // 如果检查过程中出现异常，返回false
            return false;
        }
        
        return true;
    }
    
    // 写入CSV表头
    private static void writeCsvHeader(PrintWriter writer, List<String> shp1FieldNames, String groupField, List<String> uniqueGroupValues) {
        StringBuilder header = new StringBuilder(shp1FieldNames.size() * 10 + 50); // 预分配容量
        for (String field : shp1FieldNames) {
            header.append(field).append(CSV_SEPARATOR);
        }
        header.append("Feature_Area").append(CSV_SEPARATOR)
              .append("Intersection_Area");
        // 为每个分组值生成Area列（不包含Count列）
        if (groupField != null && uniqueGroupValues != null && !uniqueGroupValues.isEmpty()) {
            for (String groupValue : uniqueGroupValues) {
                String safeValue = escapeCsvColumnName(groupValue);
                header.append(CSV_SEPARATOR).append(groupField).append("_").append(safeValue).append("_Area");
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
            .append(String.format(AREA_FORMAT, intersectionArea));
        // 按照分组值的顺序，为每个分组值只输出 Area 列（不包含Count列）
        if (groupField != null && uniqueGroupValues != null && !uniqueGroupValues.isEmpty()) {
            for (String groupValue : uniqueGroupValues) {
                GroupStats stats = groupStats != null ? groupStats.get(groupValue) : null;
                if (stats != null) {
                    line.append(CSV_SEPARATOR).append(String.format(AREA_FORMAT, stats.area));
                } else {
                    line.append(CSV_SEPARATOR).append("0.000000"); // 面积为空时输出0
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
        
        int totalFeatures = collection1.size();
        int currentFeature = 0;
        
        try (PrintWriter writer = new PrintWriter(
                new java.io.BufferedWriter(new FileWriter(csvFile, false), CSV_BUFFER_SIZE));
                SimpleFeatureIterator iterator1 = collection1.features()) {
            // 写入表头
            writeCsvHeader(writer, shp1FieldNames, groupField, uniqueGroupValues);
            
            while (iterator1.hasNext()) {
                SimpleFeature feature = iterator1.next();
                currentFeature++;
                // 每处理5%或每100个要素显示一次进度
                if (currentFeature % Math.max(1, totalFeatures / 20) == 0 || currentFeature == totalFeatures) {
                    showProgress(currentFeature, totalFeatures, "  Computing intersections");
                }
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
                        // 优化：只检查envelope相交，然后直接计算intersection
                        // 跳过耗时的intersects检查，mergedShp2是固定对象
                        if (mergedEnv.intersects(geomEnv)) {
                            try {
                                Geometry intersection = geom.intersection(mergedShp2);
                                if (intersection != null && !intersection.isEmpty()) {
                                    intersectionArea = intersection.getArea();
                                    intersectingShp2Count = 1;
                                    
                                    // 使用按分组合并后的几何体计算每个分组的交集
                                    for (Map.Entry<String, Geometry> entry : mergedGroupGeoms.entrySet()) {
                                        String groupKey = entry.getKey();
                                        Geometry mergedGroupGeom = entry.getValue();
                                        
                                        // 只检查envelope相交，直接计算intersection
                                        if (mergedGroupGeom.getEnvelopeInternal().intersects(geomEnv)) {
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

    // 检测重叠的几何对象并分组（使用并查集算法）
    private static List<List<Integer>> findOverlapGroups(List<Geometry> geometries) {
        int n = geometries.size();
        if (n == 0) {
            return new ArrayList<>();
        }
        
        // 并查集：parent[i] = i 表示i是根节点
        final int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        
        // 查找根节点（带路径压缩）
        class UnionFind {
            int find(int x) {
                if (parent[x] != x) {
                    parent[x] = find(parent[x]);
                }
                return parent[x];
            }
            
            void union(int x, int y) {
                int rootX = find(x);
                int rootY = find(y);
                if (rootX != rootY) {
                    parent[rootX] = rootY;
                }
            }
        }
        
        UnionFind uf = new UnionFind();
        
        // 构建空间索引以加速重叠检测
        org.locationtech.jts.index.strtree.STRtree index = new org.locationtech.jts.index.strtree.STRtree(STRTREE_NODE_CAPACITY);
        for (int i = 0; i < n; i++) {
            Geometry geom = geometries.get(i);
            if (geom != null && !geom.isEmpty()) {
                index.insert(geom.getEnvelopeInternal(), i);
            }
        }
        index.build();
        
        // 检测重叠并合并
        int checkedCount = 0;
        for (int i = 0; i < n; i++) {
            Geometry geom1 = geometries.get(i);
            if (geom1 == null || geom1.isEmpty()) {
                continue;
            }
            
            checkedCount++;
            
            Envelope env1 = geom1.getEnvelopeInternal();
            List<?> candidates = index.query(env1);
            
            for (Object obj : candidates) {
                int j = (Integer) obj;
                if (j <= i) { // 只检查j > i的情况，避免重复检查
                    continue;
                }
                
                Geometry geom2 = geometries.get(j);
                if (geom2 == null || geom2.isEmpty()) {
                    continue;
                }
                
                Envelope env2 = geom2.getEnvelopeInternal();
                // 先检查envelope是否相交（快速过滤）
                if (env1.intersects(env2)) {
                    try {
                        // 进一步检查几何对象是否真的重叠
                        if (geom1.intersects(geom2)) {
                            uf.union(i, j);
                        }
                    } catch (Exception e) {
                        // 忽略检查失败的情况
                    }
                }
            }
        }
        if (checkedCount > 0) {
            System.out.println(); // 换行
        }
        
        // 将并查集结果转换为分组列表
        Map<Integer, List<Integer>> groups = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int root = uf.find(i);
            groups.computeIfAbsent(root, k -> new ArrayList<>()).add(i);
        }
        
        // 只返回包含多个元素的组（单个元素不需要merge）
        List<List<Integer>> result = new ArrayList<>();
        for (List<Integer> group : groups.values()) {
            if (group.size() > 1) {
                result.add(group);
            }
        }
        
        return result;
    }
    
    // 将大的几何对象按空间网格分块，提高intersection计算效率
    private static List<Geometry> splitGeometryIntoTiles(Geometry geom, int gridSize) {
        List<Geometry> tiles = new ArrayList<>();
        Envelope geomEnv = geom.getEnvelopeInternal();
        double width = geomEnv.getWidth();
        double height = geomEnv.getHeight();
        double tileWidth = width / gridSize;
        double tileHeight = height / gridSize;
        
        GeometryFactory geomFactory = geom.getFactory();
        int totalTiles = gridSize * gridSize;
        int processedTiles = 0;
        
        for (int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                processedTiles++;
                // 每处理10%显示一次进度
                if (processedTiles % Math.max(1, totalTiles / 10) == 0 || processedTiles == totalTiles) {
                    showProgress(processedTiles, totalTiles, "  Creating tiles");
                }
                
                double minX = geomEnv.getMinX() + i * tileWidth;
                double maxX = (i == gridSize - 1) ? geomEnv.getMaxX() : geomEnv.getMinX() + (i + 1) * tileWidth;
                double minY = geomEnv.getMinY() + j * tileHeight;
                double maxY = (j == gridSize - 1) ? geomEnv.getMaxY() : geomEnv.getMinY() + (j + 1) * tileHeight;
                
                // 创建网格块的envelope
                Envelope tileEnv = new Envelope(minX, maxX, minY, maxY);
                
                // 先检查envelope是否相交，避免不必要的intersection计算
                if (!geomEnv.intersects(tileEnv)) {
                    continue; // 跳过不相交的块
                }
                
                // 创建网格块边界
                Coordinate[] coords = new Coordinate[]{
                    new Coordinate(minX, minY),
                    new Coordinate(maxX, minY),
                    new Coordinate(maxX, maxY),
                    new Coordinate(minX, maxY),
                    new Coordinate(minX, minY)
                };
                Geometry tileBoundary = geomFactory.createPolygon(coords);
                
                // 计算几何对象与网格块的intersection
                try {
                    Geometry tile = geom.intersection(tileBoundary);
                    if (tile != null && !tile.isEmpty() && tile.isValid()) {
                        tiles.add(tile);
                    }
                } catch (Exception e) {
                    // 忽略单个块的错误
                    logger.warning("Failed to create tile (" + i + "," + j + "): " + e.getMessage());
                }
            }
        }
        
        return tiles;
    }
    
    // 合并shp2后交叠统计，流式写入CSV（仅 merge 不 group 的情况）
    private static double computeIntersectionsWithMergedShp2AndWrite(SimpleFeatureCollection collection1,
            MathTransform transform1, List<String> shp1FieldNames, Geometry mergedShp2, File csvFile,
            String groupField, Map<String, List<Geometry>> groupGeoms, List<String> uniqueGroupValues) {
        double totalIntersectionArea = 0.0;
        Envelope mergedEnv = mergedShp2.getEnvelopeInternal();
        
        // 优化：快速判断是否需要分块处理
        // 使用envelope快速估算，避免计算完整顶点数（getNumPoints()很慢）
        double envWidth = mergedEnv.getWidth();
        double envHeight = mergedEnv.getHeight();
        double envArea = envWidth * envHeight;
        boolean useTiling = false;
        int gridSize = 5; // 默认5x5网格
        
        // 如果envelope面积很大，直接使用分块处理（不计算顶点数，因为那很慢）
        if (envArea > 1000000000) { // 1e9
            useTiling = true;
            // 根据envelope大小动态调整网格大小
            if (envArea > 10000000000.0) { // 1e10
                gridSize = 15; // 更大的网格
            } else if (envArea > 5000000000.0) { // 5e9
                gridSize = 10;
            } else {
                gridSize = 7;
            }
            System.out.println("  Large merged geometry detected (envelope area: " + String.format("%.0f", envArea) + 
                    "). Splitting into " + gridSize + "x" + gridSize + " tiles for faster processing...");
        } else {
            // 对于较小的几何，快速检查顶点数
            // 使用try-catch：如果getNumPoints()太慢或失败，直接使用分块
            try {
                int vertexCount = mergedShp2.getNumPoints();
                if (vertexCount > 50000) {
                    useTiling = true;
                    gridSize = 10;
                    System.out.println("  Large merged geometry detected (" + vertexCount + " vertices). Splitting into " + gridSize + "x" + gridSize + " tiles...");
                } else if (vertexCount > 20000) {
                    useTiling = true;
                    gridSize = 7;
                    System.out.println("  Large merged geometry detected (" + vertexCount + " vertices). Splitting into " + gridSize + "x" + gridSize + " tiles...");
                }
            } catch (Exception e) {
                // 如果getNumPoints()太慢或失败，直接使用分块
                useTiling = true;
                gridSize = 10;
                System.out.println("  Geometry too complex to analyze. Splitting into " + gridSize + "x" + gridSize + " tiles...");
            }
        }
        
        // 如果使用分块，创建分块和空间索引
        org.locationtech.jts.index.strtree.STRtree tileIndex = null;
        List<Geometry> tiles = null;
        if (useTiling) {
            System.out.println("  Creating tiles...");
            tiles = splitGeometryIntoTiles(mergedShp2, gridSize);
            tileIndex = new org.locationtech.jts.index.strtree.STRtree(STRTREE_NODE_CAPACITY);
            for (Geometry tile : tiles) {
                tileIndex.insert(tile.getEnvelopeInternal(), tile);
            }
            tileIndex.build();
            System.out.println("  Created " + tiles.size() + " tiles. Using tiled intersection calculation.");
        }
        
        int totalFeatures = collection1.size();
        int currentFeature = 0;
        
        try (PrintWriter writer = new PrintWriter(
                new java.io.BufferedWriter(new FileWriter(csvFile, false), CSV_BUFFER_SIZE));
                SimpleFeatureIterator iterator1 = collection1.features()) {
            // 写入表头
            writeCsvHeader(writer, shp1FieldNames, groupField, uniqueGroupValues);
            
            while (iterator1.hasNext()) {
                SimpleFeature feature = iterator1.next();
                currentFeature++;
                // 每处理5%或每100个要素显示一次进度
                if (currentFeature % Math.max(1, totalFeatures / 20) == 0 || currentFeature == totalFeatures) {
                    showProgress(currentFeature, totalFeatures, "  Computing intersections");
                }
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
                        
                        if (useTiling && tileIndex != null) {
                            // 使用分块方式：只与相关的块计算intersection
                            List<?> candidateTiles = tileIndex.query(geomEnv);
                            for (Object obj : candidateTiles) {
                                if (obj instanceof Geometry) {
                                    Geometry tile = (Geometry) obj;
                                    if (tile.getEnvelopeInternal().intersects(geomEnv)) {
                                        try {
                                            Geometry tileIntersection = geom.intersection(tile);
                                            if (tileIntersection != null && !tileIntersection.isEmpty()) {
                                                intersectionArea += tileIntersection.getArea();
                                            }
                                        } catch (Exception e) {
                                            // 忽略单个块的错误
                                        }
                                    }
                                }
                            }
                            if (intersectionArea > 0) {
                                intersectingShp2Count = 1;
                            }
                        } else {
                            // 不使用分块：直接与整个mergedShp2计算
                            if (mergedEnv.intersects(geomEnv)) {
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
        
        // 清理分块数据
        if (tiles != null) {
            tiles.clear();
        }
        
        return totalIntersectionArea;
    }


    /**
     * 对shapefile进行重投影
     * 
     * @param srcShp    输入shp路径
     * @param outShp    输出shp路径
     * @param targetCRS EPSG:xxxx、纯数字EPSG、.tif/.tiff文件路径或.shp文件路径
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
                int processedCount = 0;
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
                    processedCount++;
                    // 每处理1000个要素后提示垃圾回收
                    if (processedCount % 1000 == 0) {
                        System.gc();
                    }
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
            List<String> issueDetails, GeometryStats stats, int[] detailCount) {
        stats.total++;
        Object geometryObject = feature.getDefaultGeometry();

        if (!(geometryObject instanceof Geometry)) {
            stats.nullGeometry++;
            if (showDetail && detailCount[0] < 10000) {
                issueDetails.add(feature.getID() + ": geometry attribute is missing or not recognized");
                detailCount[0]++;
            }
            return true;
        }

        Geometry geometry = (Geometry) geometryObject;
        if (geometry.isEmpty()) {
            stats.emptyGeometry++;
            if (showDetail && detailCount[0] < 10000) {
                issueDetails.add(feature.getID() + ": geometry is empty");
                detailCount[0]++;
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

            if (showDetail && detailCount[0] < 10000) {
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
                detailCount[0]++;
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
     * 解析目标坐标系：支持EPSG:xxxx、纯数字EPSG、GeoTIFF、Shapefile自动识别。
     */
    private static CoordinateReferenceSystem resolveTargetCRS(String targetCRS) throws Exception {
        if (targetCRS == null || targetCRS.trim().isEmpty()) {
            throw new IllegalArgumentException("targetCRS is empty");
        }
        // 去除首尾可能的引号
        String trimmed = targetCRS.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || 
            (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
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

        // Shapefile 自动解析
        if (lower.endsWith(".shp")) {
            File shp = new File(trimmed);
            if (!shp.exists()) {
                throw new IllegalArgumentException("Shapefile not found: " + shp.getAbsolutePath());
            }
            // 检查配套文件
            String basePath = shp.getAbsolutePath();
            int dot = basePath.lastIndexOf('.');
            if (dot <= 0) {
                throw new IllegalArgumentException("Shapefile must have an extension");
            }
            String prefix = basePath.substring(0, dot);
            File shxFile = new File(prefix + ".shx");
            File dbfFile = new File(prefix + ".dbf");
            File prjFile = new File(prefix + ".prj");
            if (!shxFile.exists()) {
                throw new IllegalArgumentException("Missing companion file: " + shxFile.getName());
            }
            if (!dbfFile.exists()) {
                throw new IllegalArgumentException("Missing companion file: " + dbfFile.getName());
            }
            if (!prjFile.exists()) {
                throw new IllegalArgumentException("Missing companion file: " + prjFile.getName());
            }
            ShapefileDataStore store = null;
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("url", shp.toURI().toURL());
                ShapefileDataStoreFactory factory = new ShapefileDataStoreFactory();
                store = (ShapefileDataStore) factory.createDataStore(params);
                store.setCharset(Charset.forName("UTF-8"));
                CoordinateReferenceSystem crs = store.getSchema().getCoordinateReferenceSystem();
                if (crs == null) {
                    throw new IllegalArgumentException("Unable to read CRS from Shapefile (no .prj file?): " + shp.getAbsolutePath());
                }
                return crs;
            } finally {
                if (store != null) {
                    store.dispose();
                }
            }
        }



        // UTM 自动选择已不再支持，用户必须明确指定坐标系
        if (lower.equals("utm")) {
            throw new IllegalArgumentException("UTM auto-selection is no longer supported. Please specify a specific projection (e.g., EPSG:3857, EPSG:4326, or a reference TIF/SHP file).");
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

    /**
     * 计算shapefile中所有多边形的面积
     * 
     * @param shpPath  输入shapefile路径
     * @param outputCSV 输出CSV文件路径（可选，如果为null则自动生成）
     */
    public static void calculatePolygonAreas(String shpPath, String outputCSV, String projectionCRS) {
        ShapefileDataStore store = null;
        try {
            File shpFile = new File(shpPath);
            if (!shpFile.exists()) {
                System.out.println("Shapefile does not exist: " + shpPath);
                return;
            }
            if (!checkCompanionFiles(shpFile)) {
                return;
            }
            
            Map<String, Object> params = new HashMap<>();
            params.put("url", shpFile.toURI().toURL());
            ShapefileDataStoreFactory factory = new ShapefileDataStoreFactory();
            store = (ShapefileDataStore) factory.createDataStore(params);
            store.setCharset(Charset.forName("UTF-8"));

            SimpleFeatureSource featureSource = store.getFeatureSource();
            SimpleFeatureCollection collection = featureSource.getFeatures();
            SimpleFeatureType schema = collection.getSchema();
            CoordinateReferenceSystem crs = schema.getCoordinateReferenceSystem();

            // 确定用于面积计算的坐标系 - 必须使用投影坐标系以确保面积计算准确
            CoordinateReferenceSystem areaCalculationCRS = null;
            MathTransform transform = null;
            String areaUnit = "unknown";
            ReferencedEnvelope bounds = collection.getBounds();

            if (crs == null) {
                // 如果没有CRS信息，根据坐标范围推断是否为地理坐标系
                if (bounds != null && !bounds.isEmpty()) {
                    double minX = bounds.getMinX();
                    double maxX = bounds.getMaxX();
                    double minY = bounds.getMinY();
                    double maxY = bounds.getMaxY();
                    
                    // 判断是否为地理坐标系（经度-180到180，纬度-90到90）
                    boolean likelyGeographic = (minX >= -180 && maxX <= 180 && minY >= -90 && maxY <= 90);
                    
                    if (likelyGeographic) {
                        System.out.println("Warning: Shapefile missing CRS, but coordinates suggest geographic CRS.");
                        System.out.println("Converting to UTM projection for accurate area calculation.");
                        // 假设为WGS84地理坐标系
                        try {
                            crs = CRS.decode("EPSG:4326", true);
                        } catch (Exception e) {
                            System.out.println("Error: Failed to create WGS84 CRS: " + e.getMessage());
                            throw new RuntimeException("Cannot calculate area: shapefile missing CRS and cannot infer projection.", e);
                        }
                    } else {
                        System.out.println("Error: Shapefile missing CRS and coordinates do not appear to be geographic.");
                        System.out.println("Cannot determine appropriate projection for area calculation.");
                        throw new RuntimeException("Cannot calculate area: shapefile missing CRS information.");
                    }
                } else {
                    System.out.println("Error: Shapefile missing CRS and bounds are unavailable.");
                    throw new RuntimeException("Cannot calculate area: shapefile missing CRS information.");
                }
            }

            // 解析用户指定的投影，直接使用用户指定的坐标系进行转换
            try {
                if (projectionCRS == null || projectionCRS.trim().isEmpty()) {
                    throw new IllegalArgumentException("Projection CRS is required. Please specify a projection using --projection option.");
                }
                
                // 使用用户指定的投影（EPSG、TIF、SHP）
                areaCalculationCRS = resolveTargetCRS(projectionCRS);
                
                // 创建转换：直接将shapefile转化为用户指定的坐标系
                // 注意：如果原始shapefile的CRS与目标CRS相同，则跳过转换
                // 这避免了不必要的identity transform，提高性能
                if (crs != null) {
                    if (CRS.equalsIgnoreMetadata(crs, areaCalculationCRS)) {
                        transform = null; // CRS相同，跳过转换
                    } else {
                        transform = CRS.findMathTransform(crs, areaCalculationCRS, true);
                    }
                }
                
                // 确定面积单位
                try {
                    CoordinateSystem cs = areaCalculationCRS.getCoordinateSystem();
                    if (cs != null && cs.getDimension() > 0) {
                        CoordinateSystemAxis axis = cs.getAxis(0);
                        if (axis != null) {
                            String unit = axis.getUnit().toString();
                            String unitLower = unit.toLowerCase();
                            if (unitLower.contains("metre") || unitLower.contains("meter") || unitLower.equals("m")) {
                                areaUnit = "square meters";
                            } else if (unitLower.equals("ft") || unitLower.contains("foot") || unitLower.contains("feet")) {
                                areaUnit = "square feet";
                            } else if (unitLower.equals("km") || unitLower.contains("kilometre") || unitLower.contains("kilometer")) {
                                areaUnit = "square kilometers";
                            } else {
                                // 使用ASCII兼容格式，避免Unicode字符显示问题
                                areaUnit = "square " + unit;
                            }
                        }
                    }
                    if ("unknown".equals(areaUnit)) {
                        areaUnit = "square units";
                    }
                } catch (Exception e) {
                    areaUnit = "square units";
                }
            } catch (IllegalArgumentException e) {
                // 投影解析失败，直接输出错误信息并退出
                System.err.println("ERROR: " + e.getMessage());
                throw new RuntimeException("Failed to resolve projection: " + e.getMessage(), e);
            } catch (Exception e) {
                System.err.println("ERROR: Failed to resolve projection: " + e.getMessage());
                throw new RuntimeException("Failed to resolve projection: " + e.getMessage(), e);
            }

            // 获取所有非几何字段名
            List<String> fieldNames = new ArrayList<>();
            for (int i = 0; i < schema.getAttributeCount(); i++) {
                String name = schema.getDescriptor(i).getLocalName();
                if (!(schema.getDescriptor(i) instanceof GeometryDescriptor)) {
                    fieldNames.add(name);
                }
            }

            // 生成输出CSV文件名
            File csvFile;
            if (outputCSV != null && !outputCSV.trim().isEmpty()) {
                csvFile = new File(outputCSV);
            } else {
                String csvFileName = shpFile.getName();
                int dotIndex = csvFileName.lastIndexOf('.');
                String baseName = dotIndex > 0 ? csvFileName.substring(0, dotIndex) : csvFileName;
                csvFile = new File(shpFile.getParent(), baseName + "_area.csv");
            }

            // 计算面积并写入CSV
            double totalArea = 0.0;
            int featureCount = 0;
            int polygonCount = 0;

            try (PrintWriter writer = new PrintWriter(
                    new java.io.BufferedWriter(new FileWriter(csvFile, false), CSV_BUFFER_SIZE));
                    SimpleFeatureIterator iterator = collection.features()) {
                
                // 写入表头
                StringBuilder header = new StringBuilder();
                for (String field : fieldNames) {
                    header.append(field).append(CSV_SEPARATOR);
                }
                header.append("Area");
                writer.println(header.toString());

                // 处理每个要素
                while (iterator.hasNext()) {
                    SimpleFeature feature = iterator.next();
                    featureCount++;
                    Object geomObj = feature.getDefaultGeometry();
                    
                    if (geomObj instanceof Geometry) {
                        Geometry geom = (Geometry) geomObj;
                        if (!geom.isEmpty() && geom.isValid()) {
                            // 转换坐标系：如果原始shapefile的CRS与目标CRS不同，则进行转换
                            // 如果transform为null，说明CRS相同或CRS信息缺失
                            if (transform != null) {
                                try {
                                    geom = org.geotools.geometry.jts.JTS.transform(geom, transform);
                                } catch (Exception e) {
                                    logger.warning("Failed to transform geometry for feature " + feature.getID()
                                            + ": " + e.getMessage());
                                    continue;
                                }
                            }
                            // 如果transform为null且crs也为null，说明CRS信息缺失，跳过该要素
                            // 如果transform为null但crs不为null，说明CRS相同，直接使用原几何对象
                            if (transform == null && crs == null) {
                                logger.warning("Skipping feature " + feature.getID() + ": CRS information missing, cannot determine if transformation is needed");
                                continue;
                            }

                            String geomType = geom.getGeometryType();
                            if ("Polygon".equalsIgnoreCase(geomType) || "MultiPolygon".equalsIgnoreCase(geomType)) {
                                double area = geom.getArea();
                                totalArea += area;
                                polygonCount++;

                                // 写入CSV行
                                StringBuilder line = new StringBuilder();
                                for (String fieldName : fieldNames) {
                                    Object val = feature.getAttribute(fieldName);
                                    line.append(escapeCsv(val != null ? val.toString() : "")).append(CSV_SEPARATOR);
                                }
                                line.append(String.format(AREA_FORMAT, area));
                                writer.println(line.toString());
                            }
                        }
                    }
                    
                    // 每处理1000个要素后提示垃圾回收
                    if (featureCount % 1000 == 0) {
                        System.gc();
                    }
                }
            } catch (IOException e) {
                System.out.println("Failed to write CSV file: " + e.getMessage());
                e.printStackTrace();
                return;
            }

            // 输出统计结果
            System.out.println("=== Polygon Area Calculation ===");
            System.out.println("Shapefile: " + shpFile.getAbsolutePath());
            System.out.println("Total features: " + featureCount);
            System.out.println("Polygon/MultiPolygon features: " + polygonCount);
            System.out.println("--- Area Calculation Settings ---");
            System.out.println("Coordinate System (CRS): "
                    + (areaCalculationCRS != null ? areaCalculationCRS.getName().toString() : "unknown"));
            System.out.println("Area Unit: " + areaUnit);
            System.out.println("Total area: " + String.format("%.6f", totalArea) + " " + areaUnit);
            System.out.println("CSV file saved to: " + csvFile.getAbsolutePath());

        } catch (Exception e) {
            System.out.println("Failed to calculate polygon areas: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (store != null) {
                store.dispose();
            }
        }
    }
}
