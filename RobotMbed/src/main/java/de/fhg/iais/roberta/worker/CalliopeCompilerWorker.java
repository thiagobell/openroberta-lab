package de.fhg.iais.roberta.worker;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.sun.org.apache.xalan.internal.xsltc.cmdline.Compile;
import de.fhg.iais.roberta.syntax.lang.expr.Expr;
import jdk.nashorn.internal.codegen.CompilationException;
import jdk.nashorn.internal.codegen.CompilerConstants;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.SystemUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.fhg.iais.roberta.bean.CompilerSetupBean;
import de.fhg.iais.roberta.components.Project;
import de.fhg.iais.roberta.util.Key;
import de.fhg.iais.roberta.util.Util;
import de.fhg.iais.roberta.util.basic.Pair;


public class CalliopeCompilerWorker implements ICompilerWorker {
    private static final Logger LOG = LoggerFactory.getLogger(CalliopeCompilerWorker.class);

    @Override
    public void execute(Project project) {
        String programName = project.getProgramName();
        String robot = project.getRobot();
        final CompilerSetupBean compilerWorkflowBean = project.getWorkerResult(CompilerSetupBean.class);


        final String compilerBinDir = compilerWorkflowBean.getCompilerBinDir();
        final String compilerResourcesDir = compilerWorkflowBean.getCompilerResourcesDir();
        final String tempDir = compilerWorkflowBean.getTempDir();
        final String crossCompilerSource = project.getSourceCode().toString();

        String token = project.getToken();
        String sourceCodeFileExtension = project.getSourceCodeFileExtension();

        boolean runRemotely = true;
        String endpoint = "http://localhost:1999/rest/mbed/calliope/compilerJob";

        CompileRequest cr = new CompileRequest(
                crossCompilerSource,
                robot,
                token,
                programName,
                project.isNativeEditorCode()
        );
        CompileResponse buildResult;

        if(runRemotely){
            
            try {
                buildResult = CalliopeCompilerWorker.runBuildRemotely(
                        endpoint,
                        cr
                );
            } catch (RemoteCompilationException e) {
                // if there is an exception we have to use the existing method of returning error messages
                buildResult = new CompileResponse(
                        Key.COMPILERWORKFLOW_ERROR_PROGRAM_COMPILE_FAILED,
                        null,
                        e.toString()
                );
            }
        } else {
            buildResult = CalliopeCompilerWorker.runBuild(
                    cr,
                    sourceCodeFileExtension,
                    project.getBinaryFileExtension(),
                    compilerBinDir,
                    compilerResourcesDir,
                    tempDir
            );
        }

        // TODO: check how to do this sensibly, without having the UsedHardwareWorker beforehand
        project.setResult(buildResult.statusKey);
        project.addResultParam("MESSAGE", buildResult.compilerMessage);
        if ( buildResult.statusKey == Key.COMPILERWORKFLOW_SUCCESS ) {
            LOG.info("compile {} program {} successful", robot, programName);
        } else {
            LOG.error("compile of program {} for robot {} failed with message: {}: {}", programName, robot,
                    buildResult.statusKey, buildResult.compilerMessage);
        }
    }

    /**
     * create command to call the crosscompiler and execute the call.
     *
     * @return a pair of Key.COMPILERWORKFLOW_SUCCESS or Key.COMPILERWORKFLOW_ERROR_PROGRAM_COMPILE_FAILED and the cross compiler output
     */

    private static CompileResponse runBuildRemotely(
            String endpoint,
            CompileRequest request) throws RemoteCompilationException {

        try {

            // creates the payload

            JSONObject payload = new JSONObject();
            // need to wrap the request so the roberta server can deserialize it
            payload.put("data", request.to_json());


            URL url = new URL(endpoint);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Accept", "application/json");
            con.setDoOutput(true);
            con.setConnectTimeout(5000);
            con.setReadTimeout(15000);

            try (OutputStream os = con.getOutputStream()) {

                byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // POST should have been sent after this
            if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
                // request successful


                return CompileResponse.from_json(response.toString());

            } else {
                throw new RemoteCompilationException("Request Failed: Status Code:" + con.getResponseCode() +
                        " Body: " + response);
            }
        } catch (IOException e) {
            throw new RemoteCompilationException("IO Error: " + e.toString());
        }

    }

    public static CompileResponse runBuild(
            CompileRequest request,
            String sourceCodeFileExtension,
            String binaryFileExtension,
            String compilerBinDir,
            String compilerResourcesDir,
            String tempDir
    ) {
        Util.storeGeneratedProgram(tempDir, request.crossCompilerSource, request.token, request.programName,
                "." + sourceCodeFileExtension);
        String scriptName = compilerResourcesDir + "../compile." + (SystemUtils.IS_OS_WINDOWS ? "bat" : "sh");
        boolean bluetooth = request.robot.equals("calliope2017");
        String bluetoothParam = bluetooth ? "-b" : "";
        Path pathToSrcFile = Paths.get(tempDir + request.token + "/" + request.programName);

        String[] executableWithParameters =
            {
                scriptName,
                compilerBinDir,
                request.programName,
                Paths.get("").resolve(pathToSrcFile).toAbsolutePath().normalize() + "/",
                compilerResourcesDir,
                bluetoothParam
            };

        Pair<Boolean, String> result = Util.runCrossCompiler(executableWithParameters, request.crossCompilerSource,
                request.isNativeEditorCode);
        Key resultKey = result.getFirst() ? Key.COMPILERWORKFLOW_SUCCESS : Key.COMPILERWORKFLOW_ERROR_PROGRAM_COMPILE_FAILED;
        String compiledHex = null;
        if ( result.getFirst() ) {
            try {
                compiledHex = FileUtils
                            .readFileToString(
                                new File(pathToSrcFile + "/target/" + request.programName + "." + binaryFileExtension),
                                StandardCharsets.UTF_8);
                resultKey = Key.COMPILERWORKFLOW_SUCCESS;
            } catch ( IOException e ) {
                LOG.error("compilation of Calliope program successful, but reading the binary failed", e);
                resultKey = Key.COMPILERWORKFLOW_ERROR_PROGRAM_COMPILE_FAILED;
            }
        }
        return new CompileResponse(resultKey, result.getSecond(), compiledHex);
    }
}
