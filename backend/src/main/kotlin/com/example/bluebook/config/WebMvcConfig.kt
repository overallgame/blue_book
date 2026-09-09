package com.example.bluebook.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(
    @Value("\${app.upload.storage-path}") private val storagePath: String,
    @Value("\${app.upload.hls-path:/opt/blue-book/hls}") private val hlsPath: String
) : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOrigins("*")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
    }

    // static media mapping so dev (no nginx) can fetch uploads and hls
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/upload/**").addResourceLocations("file:$storagePath/")
        registry.addResourceHandler("/hls/**").addResourceLocations("file:$hlsPath/")
    }
}
