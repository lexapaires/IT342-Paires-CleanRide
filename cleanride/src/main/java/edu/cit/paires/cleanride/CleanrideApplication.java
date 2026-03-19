package edu.cit.paires.cleanride;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootApplication
public class CleanrideApplication {

	public static void main(String[] args) {
		SpringApplication.run(CleanrideApplication.class, args);
	}

	@Bean
	public CommandLineRunner dropConstraints(JdbcTemplate jdbcTemplate) {
		return args -> {
			try {
				jdbcTemplate.execute("ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_status_check;");
				System.out.println("✅ Successfully removed outdated status check constraint!");
			} catch (Exception e) {
				System.err.println("Notice: Could not drop constraint: " + e.getMessage());
			}
		};
	}
}
