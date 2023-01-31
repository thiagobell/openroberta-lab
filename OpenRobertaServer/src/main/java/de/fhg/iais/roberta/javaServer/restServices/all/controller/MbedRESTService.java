    package de.fhg.iais.roberta.javaServer.restServices.all.controller;

    import de.fhg.iais.roberta.factory.RobotFactory;
    import de.fhg.iais.roberta.generated.restEntities.FullRestRequest;
    import de.fhg.iais.roberta.javaServer.provider.OraData;
    import de.fhg.iais.roberta.persistence.util.DbSession;
    import de.fhg.iais.roberta.persistence.util.HttpSessionState;
    import de.fhg.iais.roberta.util.UtilForREST;
    import de.fhg.iais.roberta.worker.CalliopeCompilerWorker;
    import de.fhg.iais.roberta.worker.CompileRequest;
    import de.fhg.iais.roberta.worker.CompileResponse;
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;

    import javax.ws.rs.*;
    import javax.ws.rs.core.MediaType;
    import javax.ws.rs.core.Response;

    @Path("/mbed")
    public class MbedRESTService {

        private static final Logger LOG = LoggerFactory.getLogger(MbedRESTService.class);

        // todo set these up
        // some stubs to compiler config
        static String compilerBinDir = "/";
        static String compilerResourcesDir = "/";
        static String tempDir = "/tmp";

        @Path("/calliope/compilerJob")
        @POST
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        public Response compile_calliope(
                @OraData DbSession dbSession, // needs to be removed
                FullRestRequest fullRequest
        ) {

            // this HAS to go, so it can be reimplemented separately
            // only used to query the robots static configuration....
            // ... which should not depend on some HTTP session
            HttpSessionState httpSessionState = UtilForREST.handleRequestInit(dbSession, LOG,
                    fullRequest, true);
            RobotFactory robotFactory = httpSessionState.getRobotFactory();



            CompileRequest request = CompileRequest.from_json(fullRequest.getData());

            CompileResponse response = CalliopeCompilerWorker.runBuild(
                    request,
                    robotFactory.getSourceCodeFileExtension(),
                    robotFactory.getBinaryFileExtension(),
                    robotFactory.getPluginProperties().getCompilerBinDir(),
                    robotFactory.getPluginProperties().getCompilerResourceDir(),
                    robotFactory.getPluginProperties().getTempDir());

            // CalliopeCompilerWorker.runBuild()
            return Response.ok(response.to_json()).build();
        }

    }
