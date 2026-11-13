package com.atamanahmet.beamlink.peer;

import com.atamanahmet.beamlink.peer.service.NetworkRegistrationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application entry point for Beamlink Device
 * Runs on each PC in the network
 */
@SpringBootApplication(
        exclude = {
                org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration.class
        }
)
@EnableScheduling
@RequiredArgsConstructor
@EnableAsync
public class BeamlinkPeerApplication implements CommandLineRunner {

    @Value("${server.port}")
    private String port;

    @Value("${peer.ip-address}")
    private String ipAddress;

    private final Logger log = LoggerFactory.getLogger(BeamlinkPeerApplication.class);
    private final NetworkRegistrationService networkRegistrationService;


    public static void main(String[] args) {
        SpringApplication.run(BeamlinkPeerApplication.class, args);
    }

    @Override
    public void run(String... args) {
        System.out.println("========================================");
        System.out.println("BEAMLINK PEER STARTED");
        System.out.println("========================================");
        System.out.println("Device UI: http://"+ipAddress+":"+port);
        System.out.println("========================================");
        System.out.println("Update received");

        networkRegistrationService.startEnrollment();

    }
}