namespace Pythontopia.Core.Expedition;

/// <summary>
/// 课程包 43《Python 语言基础》65 个 KC 的中文名 — 端口自 web <c>frontend/src/data/kcNames.ts</c>
/// （与后端 <c>course_kc.kc_name</c> 一致）。面向学生的文案不暴露 <c>KC-002</c> 这种原始编码。
/// 课程底座固定（packId=43，65 KC），故用静态映射，同步可用、无需等 API。
/// </summary>
public static class KcNames
{
    public static readonly IReadOnlyDictionary<string, string> Names = new Dictionary<string, string>
    {
        ["PY43-KC-001"] = "中国计算机发展成就",
        ["PY43-KC-002"] = "计算工具的演变",
        ["PY43-KC-003"] = "计算思维的方法与特征",
        ["PY43-KC-004"] = "计算思维的概念与定义",
        ["PY43-KC-005"] = "计算机发展历程与趋势",
        ["PY43-KC-006"] = "计算的概念",
        ["PY43-KC-007"] = "计算科学与计算机科学",
        ["PY43-KC-008"] = "Python 开发环境配置与程序运行",
        ["PY43-KC-009"] = "Python 语言概述",
        ["PY43-KC-010"] = "变量与运算符",
        ["PY43-KC-011"] = "基本数据类型",
        ["PY43-KC-012"] = "基础函数",
        ["PY43-KC-013"] = "控制台输入与数据类型转换",
        ["PY43-KC-014"] = "程序的运行方式",
        ["PY43-KC-015"] = "程序设计语言发展历史",
        ["PY43-KC-016"] = "程序运行原理",
        ["PY43-KC-017"] = "0 和 1 的计算思维",
        ["PY43-KC-018"] = "format() 方法的格式控制",
        ["PY43-KC-019"] = "Python 数值类型与运算",
        ["PY43-KC-020"] = "Unicode 编码与字符函数",
        ["PY43-KC-021"] = "二进制四则运算",
        ["PY43-KC-022"] = "字符串处理方法",
        ["PY43-KC-023"] = "字符串格式化",
        ["PY43-KC-024"] = "字符串索引与切片",
        ["PY43-KC-025"] = "整数位数提取",
        ["PY43-KC-026"] = "文字信息编码",
        ["PY43-KC-027"] = "汉字编码体系",
        ["PY43-KC-028"] = "第三方库的使用",
        ["PY43-KC-029"] = "进位计数制与二进制",
        ["PY43-KC-030"] = "进制转换",
        ["PY43-KC-031"] = "for 循环与遍历",
        ["PY43-KC-032"] = "while 循环与循环分支结合",
        ["PY43-KC-033"] = "分支结构",
        ["PY43-KC-034"] = "分支语句嵌套",
        ["PY43-KC-035"] = "多条件判断与逻辑运算",
        ["PY43-KC-036"] = "循环嵌套",
        ["PY43-KC-037"] = "循环控制语句",
        ["PY43-KC-038"] = "顺序结构",
        ["PY43-KC-039"] = "Random 模块与列表",
        ["PY43-KC-040"] = "列表基本操作",
        ["PY43-KC-041"] = "字典在实际问题中的应用",
        ["PY43-KC-042"] = "字典基本操作",
        ["PY43-KC-043"] = "字典类型",
        ["PY43-KC-044"] = "序列基本操作",
        ["PY43-KC-045"] = "序列类型",
        ["PY43-KC-046"] = "集合的删除操作",
        ["PY43-KC-047"] = "集合类型",
        ["PY43-KC-048"] = "Python 文件操作",
        ["PY43-KC-049"] = "基于文件的数据分析",
        ["PY43-KC-050"] = "文件写入操作",
        ["PY43-KC-051"] = "文件系统基础",
        ["PY43-KC-052"] = "文件读取与数据处理",
        ["PY43-KC-053"] = "词频统计",
        ["PY43-KC-054"] = "lambda 函数",
        ["PY43-KC-055"] = "pyinstaller 打包",
        ["PY43-KC-056"] = "Python 包",
        ["PY43-KC-057"] = "Python 库与 pip 工具",
        ["PY43-KC-058"] = "主程序与 __name__ 变量",
        ["PY43-KC-059"] = "函数参数高级用法",
        ["PY43-KC-060"] = "函数变量的作用域",
        ["PY43-KC-061"] = "函数递归",
        ["PY43-KC-062"] = "模块与导入",
        ["PY43-KC-063"] = "模块化编程",
        ["PY43-KC-064"] = "类和对象基础",
        ["PY43-KC-065"] = "面向对象编程的特征",
    };

    /// <summary>KC code → 中文名；支持 <c>PY43-KC-002</c> 与 <c>KC-002</c> 两种写法；未知回退到原串。</summary>
    public static string DisplayName(string? code)
    {
        if (string.IsNullOrEmpty(code))
        {
            return "本知识点";
        }
        var full = code.StartsWith("PY43-", StringComparison.Ordinal) ? code : $"PY43-{code}";
        if (Names.TryGetValue(full, out var name))
        {
            return name;
        }
        return Names.TryGetValue(code, out var alt) ? alt : code;
    }
}
