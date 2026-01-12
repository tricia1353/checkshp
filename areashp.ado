cap program drop areashp
program define areashp
    version 16
    
    syntax anything [, SAVE(string)]
    
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
    
    * 处理输出CSV文件路径（如果指定）
    local output_csv ""
    if "`save'" != "" {
        normalize_path, file(`"`save'"')
        local output_csv `"`r(filepath)'"'
    }
    
    * 构建面积计算命令（确保所有路径都用引号包裹）
    local cmd `""`java_path'" -jar "`jar_path'" "`shpfile'" area"'
    if "`output_csv'" != "" {
        local cmd `"`cmd' "`output_csv'""'
    }
    
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
