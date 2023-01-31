package de.fhg.iais.roberta.worker;

import de.fhg.iais.roberta.util.Key;

import org.json.JSONObject;

/**
 * a simple container class to wrap return values from CalliopeCompilerWorker
 */
public class CompileResponse {
    final public Key statusKey;
    final public String compilerMessage;
    final public String compilerHex;

    public CompileResponse(Key statusKey, String compilerMessage, String compilerHex) {
        this.statusKey = statusKey;
        this.compilerHex = compilerHex;
        this.compilerMessage = compilerMessage;
    }

    public String to_json() {
        JSONObject jo = new JSONObject();
        jo.put("statusKey", this.statusKey.toString());
        jo.put("compilerMessage", this.compilerMessage);
        jo.put("compilerHex", this.compilerHex);
        return jo.toString();
    }

    public static CompileResponse from_json(String buildResponseJson) {
        JSONObject jo = new JSONObject(buildResponseJson);

        return new CompileResponse(
                Key.valueOf(jo.getString("statusKey")),
                jo.getString("compilerMessage"),
                jo.getString("compilerHex")
        );
    }

}
