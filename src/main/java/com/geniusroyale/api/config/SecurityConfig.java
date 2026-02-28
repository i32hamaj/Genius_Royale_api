package com.geniusroyale.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder; // <-- ¡CAMBIO IMPORTANTE!
import org.springframework.security.web.SecurityFilterChain;

// --- En tu Backend Spring Boot ---
// Asegúrate de tener esta configuración para permitir CORS

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .cors() // <--- ACTIVA CORS AQUÍ
            .and()
            .csrf().disable() // Desactivar CSRF (necesario para APIs REST si no usas cookies)
            .authorizeRequests()
            // ... tus reglas de autorización ...
            .antMatchers("/api/auth/**").permitAll() // Permitir login/registro
            .anyRequest().authenticated();
            // ... resto de config ...
    }

    // Bean para configurar CORS específicamente
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // OJO: En producción, cambia "*" por la URL real de tu frontend web
        configuration.setAllowedOrigins(Arrays.asList("*")); 
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("authorization", "content-type", "x-auth-token"));
        configuration.setExposedHeaders(Arrays.asList("x-auth-token"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}