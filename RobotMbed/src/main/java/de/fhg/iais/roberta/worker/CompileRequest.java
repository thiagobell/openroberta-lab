package de.fhg.iais.roberta.worker;
import org.json.JSONObject;
final public class CompileRequest {
    final public String crossCompilerSource;
    final public String robot;
    final public String token;
    final public Boolean isNativeEditorCode;
    final public String programName;

    public CompileRequest(
            String crossCompilerSource,
            String robot,
            String token,
            String programName,
            Boolean isNativeEditorCode
    ) {
        this.crossCompilerSource = crossCompilerSource;
        this.robot = robot;
        this.programName = programName;
        this.token = token;
        this.isNativeEditorCode = isNativeEditorCode;
    }

    public String to_json() {
        JSONObject jo = new JSONObject();
        jo.put("crossCompilerSource", this.crossCompilerSource);
        jo.put("robot", this.robot);
        jo.put("token", this.token);
        jo.put("programName", this.programName);
        jo.put("isNativeEditorCode", this.isNativeEditorCode);
        return jo.toString();
    }

    public static CompileRequest from_json(String compileRequestJson) {
        JSONObject jo = new JSONObject(compileRequestJson);
        return CompileRequest.from_json(jo);
    }

    public static CompileRequest from_json(JSONObject jo) {

        return new CompileRequest(
                jo.getString("crossCompilerSource"),
                jo.getString("robot"),
                jo.getString("token"),
                jo.getString("programName"),
                jo.getBoolean("isNativeEditorCode")
        );
    }

}

