package io.mosip.registration.controller;

import com.sun.javafx.application.LauncherImpl;
import io.mosip.registration.controller.eodapproval.RegistrationApprovalController;
import io.mosip.registration.preloader.ClientPreLoader;
import com.techno.regclient.ClientServer;
import io.mosip.registration.service.packet.RegistrationApprovalService;

import java.nio.file.Path;


public class Initialization {

    public static void main(String[] args) throws Exception {

        System.setProperty("java.net.useSystemProxies", "true");
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("logback.configurationFile", Path.of("lib", "logback.xml").toFile().getCanonicalPath());   //NOSONAR Setting logger configuration file path here.

//        // start server socket
//        ClientServer.StartServer();

//        lauch application
        LauncherImpl.launchApplication(ClientApplication.class, ClientPreLoader.class, args);

    }
}
