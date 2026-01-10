*! checkshp v1.0.0
*! Chunxia Chen, School of Management, Xiamen University
*! triciachen6754@126.com
*! 2025-01-10

program define checkshp
    version 16
    
    syntax anything [, Detail Summary Clean REPROject(string) INTERSect(string) MERGE GROUP(string)]
    
    * 解析并处理主文件路径（参考 gtiffdisp.ado 的方式）
    local shpfile `anything'
    normalize_path, file(`"`shpfile'"')
    local shpfile `"`r(filepath)'"'
    capture confirm file `"`shpfile'"'
    if _rc {
        display as error `"shapefile not found: `shpfile'"'
        exit 601
    }
    
    * 自动确定 Java 路径
    capture java query
    if _rc == 0 {
        local java_home_dir `"`c(java_home)'"'
        local java_path `"`java_home_dir'bin/java.exe"'
        local java_path : subinstr local java_path "\" "/", all
        * 确保 java_path 被正确引用（包含空格时）
        capture confirm file `"`java_path'"'
        if _rc {
            display as error "Java not found. Please install Java and configure it in Stata using {cmd:set java_home}."
            exit 601
        }
    }
    else {
        * 尝试系统 PATH 中的 java
        local java_path "java"
    }
    
    * 自动查找 JAR 文件路径
    * 尝试在多个可能的位置查找 JAR 文件
    local jar_path `"`c(pwd)'/checkshp-0.1.0.jar"'
    local jar_path : subinstr local jar_path "\" "/", all
    capture confirm file `"`jar_path'"'
    if _rc {
        * 尝试当前目录下的 build/libs 目录
        local jar_path `"`c(pwd)'/build/libs/checkshp-0.1.0.jar"'
        local jar_path : subinstr local jar_path "\" "/", all
        capture confirm file `"`jar_path'"'
        if _rc {
            * 尝试在 ado 目录下查找（如果 ado 文件安装在 Stata 的 ado 目录中）
            local ado_dir : sysdir PLUS
            local jar_path `"`ado_dir'c/checkshp-0.1.0.jar"'
            local jar_path : subinstr local jar_path "\" "/", all
            capture confirm file `"`jar_path'"'
            if _rc {
                display as error "JAR file not found. Searched in:"
                display as error `"  `c(pwd)'/checkshp-0.1.0.jar"'
                display as error `"  `c(pwd)'/build/libs/checkshp-0.1.0.jar"'
                display as error `"  `ado_dir'c/checkshp-0.1.0.jar"'
                exit 601
            }
        }
    }
    
    * 验证选项的互斥性
    if ("`detail'" != "" & "`summary'" != "") {
        display as error "Options detail and summary are mutually exclusive."
        exit 198
    }
    
    if ("`intersect'" != "" & "`reproject'" != "") {
        display as error "Options intersect and reproject are mutually exclusive."
        exit 198
    }
    
    if ("`intersect'" != "" & ("`detail'" != "" | "`summary'" != "")) {
        display as error "Options intersect cannot be used with detail or summary."
        exit 198
    }
    
    * 处理相交模式
    if `"`intersect'"' != "" {
        local intersect_file `intersect'
        normalize_path, file(`"`intersect_file'"')
        local intersect_file `"`r(filepath)'"'
        capture confirm file `"`intersect_file'"'
        if _rc {
            display as error `"Intersect shapefile not found: `intersect_file'"'
            exit 601
        }
        
        * 验证相交模式下不能使用某些选项
        if "`clean'" != "" {
            display as error "Option clean is not applicable in intersect mode."
            exit 198
        }
        
        * 构建相交命令（确保所有路径都用引号包裹）
        local cmd `""`java_path'" -jar "`jar_path'" "`shpfile'" intersect "`intersect_file'""'
        
        if "`merge'" != "" {
            local cmd `"`cmd' --merge-shp2"'
        }
        
        if "`group'" != "" {
            * 处理 group 字段名，去除引号（字段名不应包含引号）
            * Stata 的 syntax GROUP(string) 会自动去除引号，但我们再次确保
            local group_field "`group'"
            removequotes, file("`group_field'")
            local group_field `r(file)'
            * 去除首尾空格和可能残留的引号
            local group_field = trim(`"`group_field'"')
            if substr(`"`group_field'"', 1, 1) == `"""' {
                local group_field = substr(`"`group_field'"', 2, .)
            }
            if substr(`"`group_field'"', -1, 1) == `"""' {
                local len = length(`"`group_field'"') - 1
                if `len' > 0 {
                    local group_field = substr(`"`group_field'"', 1, `len')
                }
            }
            * 直接传递字段名，不需要引号包裹（字段名通常不包含空格）
            local cmd `"`cmd' --group-field `group_field'"'
        }
        
        shell `cmd'
        exit
    }
    
    * 处理重投影模式
    if `"`reproject'"' != "" {
        * reproject 参数可能是 EPSG 代码或 GeoTIFF 文件路径
        local reproj_param `"`reproject'"'
        
        * 判断是否为 EPSG 代码：EPSG:4326 格式或纯数字
        local is_epsg = 0
        if strmatch(`"`reproj_param'"', "EPSG:*") {
            local is_epsg = 1
        }
        else if regexm(`"`reproj_param'"', "^[0-9]+$") {
            * 纯数字，自动添加 EPSG: 前缀
            local reproj_param `"EPSG:`reproj_param'"'
            local is_epsg = 1
        }
        
        * 如果不是 EPSG 代码，则视为文件路径，进行规范化处理
        if !`is_epsg' {
            local reproj_file `reproj_param'
            normalize_path, file(`"`reproj_file'"')
            local reproj_param `"`r(filepath)'"'
            * 检查文件是否存在
            capture confirm file `"`reproj_param'"'
            if _rc {
                display as error `"Reproject target file not found: `reproj_param'"'
                exit 601
            }
        }
        
        * 确定输出模式（detail 或 summary）
        local output_mode "summary"
        if "`detail'" != "" {
            local output_mode "detail"
        }
        
        * 确定是否删除无效要素
        local delete_flag "false"
        if "`clean'" != "" {
            local delete_flag "true"
        }
        
        if `"`reproj_param'"' != "" {
            local cmd `""`java_path'" -jar "`jar_path'" "`shpfile'" `output_mode' `delete_flag' "`reproj_param'""'
        }
        else {
            local cmd `""`java_path'" -jar "`jar_path'" "`shpfile'" `output_mode' `delete_flag'""'
        }
        
        shell `cmd'
        exit
    }
    
    * 处理检查模式（默认）
    local output_mode "summary"
    if "`detail'" != "" {
        local output_mode "detail"
    }
    
    local delete_flag "false"
    if "`clean'" != "" {
        local delete_flag "true"
    }
    
    local cmd `""`java_path'" -jar "`jar_path'" "`shpfile'" `output_mode' `delete_flag'""'
    
    shell `cmd'
    
end


cap program drop removequotes
program define removequotes, rclass
    version 16
    syntax, file(string)
    return local file `file'
end


cap program drop normalize_path
program define normalize_path, rclass
    version 16
    syntax, file(string)
    
    removequotes, file(`file')
    local filepath `r(file)'
    
    local filepath : subinstr local filepath "\" "/", all
    
    local path_len = length(`"`filepath'"')
    local is_absolute = 0
    
    if `path_len' >= 3 {
        local first_char = substr(`"`filepath'"', 1, 1)
        local second_char = substr(`"`filepath'"', 2, 1)
        local third_char = substr(`"`filepath'"', 3, 1)
        
        if regexm(`"`first_char'"', "[a-zA-Z]") & `"`second_char'"' == ":" & `"`third_char'"' == "/" {
            local is_absolute = 1
        }
    }
    
    if !`is_absolute' & substr(`"`filepath'"', 1, 1) == "/" {
        local is_absolute = 1
    }
    
    if !`is_absolute' {
        local pwd `"`c(pwd)'"'
        local pwd : subinstr local pwd "\" "/", all
        * 确保 pwd 以斜杠结尾
        if substr(`"`pwd'"', -1, 1) != "/" {
            local pwd = `"`pwd'/"'
        }
        local filepath = `"`pwd'`filepath'"'
    }
    
    local filepath : subinstr local filepath "\" "/", all
    
    return local filepath `"`filepath'"'
end
