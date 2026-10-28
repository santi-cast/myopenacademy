package dev.codeacademy;

import org.springframework.boot.SpringApplication;

public class TestCodeAcademyApplication {

	public static void main(String[] args) {
		SpringApplication.from(CodeAcademyApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
