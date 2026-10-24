package es.vicai.studylab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StudyLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudyLabApplication.class, args);
    }
}
