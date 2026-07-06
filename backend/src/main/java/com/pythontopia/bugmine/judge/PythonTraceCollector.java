package com.pythontopia.bugmine.judge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.submission.judge.JudgeTestCase;
import com.pythontopia.submission.judge.PythonSandbox;
import com.pythontopia.submission.judge.SandboxRunResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 在沙箱中重跑学生代码以收集有上限的逐行变量轨迹：注入使用 {@code sys.settrace} 的小型包装器，
 * 把轨迹以带围栏标记的 JSON 块写入 stdout，供调用方解析而不影响沙箱接口。
 */
@Component
public class PythonTraceCollector {

  private static final Logger log = LoggerFactory.getLogger(PythonTraceCollector.class);
  private static final String TRACE_FENCE_START = "##PYT_TRACE_START##";
  private static final String TRACE_FENCE_END = "##PYT_TRACE_END##";
  private static final int MAX_STEPS = 200;

  private final PythonSandbox sandbox;
  private final ObjectMapper objectMapper;

  public PythonTraceCollector(PythonSandbox sandbox, ObjectMapper objectMapper) {
    this.sandbox = sandbox;
    this.objectMapper = objectMapper;
  }

  public TraceResult collect(String code, String stdin) {
    String wrapper = buildWrapper(code);
    SandboxRunResult result = sandbox.run(wrapper, new JudgeTestCase(stdin, "", 6000));
    ArrayNode steps = objectMapper.createArrayNode();
    boolean truncated = false;
    String stdout = result.stdout() == null ? "" : result.stdout();
    int startIdx = stdout.indexOf(TRACE_FENCE_START);
    int endIdx = stdout.indexOf(TRACE_FENCE_END);
    if (startIdx >= 0 && endIdx > startIdx) {
      String tracePayload = stdout.substring(startIdx + TRACE_FENCE_START.length(), endIdx).trim();
      try {
        JsonNode parsed = objectMapper.readTree(tracePayload);
        if (parsed.isArray()) {
          for (JsonNode step : parsed) {
            if (steps.size() >= MAX_STEPS) {
              truncated = true;
              break;
            }
            steps.add(step);
          }
        }
      } catch (Exception exception) {
        log.warn("Failed to parse trace payload: {}", exception.getMessage());
      }
    }
    ObjectNode summary = objectMapper.createObjectNode();
    summary.set("steps", steps);
    summary.put("truncated", truncated);
    summary.put("sandbox", sandbox.name());
    summary.put("durationMs", result.durationMs());
    return new TraceResult(summary, steps.size(), truncated);
  }

  private String buildWrapper(String code) {
    String escaped = code.replace("\\", "\\\\").replace("\"\"\"", "\\\"\\\"\\\"");
    return """
        import sys, json
        STUDENT_CODE = \"\"\"%s\"\"\"
        TRACE = []
        MAX = %d
        def _tracer(frame, event, arg):
            if event == 'line' and frame.f_code.co_filename == '<student>':
                if len(TRACE) >= MAX:
                    return None
                locals_repr = {}
                for k, v in list(frame.f_locals.items()):
                    if k.startswith('_') or k in ('STUDENT_CODE', 'TRACE', 'MAX'):
                        continue
                    try:
                        r = repr(v)
                    except Exception:
                        r = '<unrepr>'
                    if len(r) > 200:
                        r = r[:200] + '...'
                    locals_repr[k] = r
                TRACE.append({'line': frame.f_lineno, 'locals': locals_repr})
            return _tracer
        try:
            compiled = compile(STUDENT_CODE, '<student>', 'exec')
            sys.settrace(_tracer)
            exec(compiled, {'__name__': '__main__'})
        except SystemExit:
            pass
        except Exception as exc:
            sys.stderr.write('TRACE_EXC:' + type(exc).__name__ + ':' + str(exc))
        finally:
            sys.settrace(None)
            sys.stdout.write('\\n##PYT_TRACE_START##' + json.dumps(TRACE) + '##PYT_TRACE_END##\\n')
        """
        .formatted(escaped, MAX_STEPS);
  }

  public record TraceResult(ObjectNode summary, int stepCount, boolean truncated) {}
}
