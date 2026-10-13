package com.atamanahmet.beamlink.nexus;

import com.atamanahmet.beamlink.nexus.config.NexusConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class BeamlinkNexusApplication {

    public static void main(String[] args) {

        var ctx = SpringApplication.run(BeamlinkNexusApplication.class, args);

        NexusConfig nexusConfig = ctx.getBean(NexusConfig.class);
        String ip = nexusConfig.getIp();
        int port = nexusConfig.getNexusPort();

        System.out.println(" ");
        System.out.println("==================================");
        System.out.println("🌐 BEAMLINK NEXUS STARTED");
        System.out.println("==================================");
        System.out.println("Dashboard: http://" + ip + ":" + port);
        System.out.println("API:       http://" + ip + ":" + port);
        System.out.println("==================================");
        System.out.println(" ");

    }
}