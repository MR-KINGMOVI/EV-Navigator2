package com.evnavigator.iran.core.weather.provider

/**
 * Errors that can occur when interacting with weather providers.
 */
enum class WeatherProviderError {
    /**
     * Network connectivity issues
     */
    NETWORK_ERROR,
    
    /**
     * Request timeout
     */
    TIMEOUT,
    
    /**
     * Rate limit exceeded
     */
    RATE_LIMITED,
    
    /**
     * Authentication failure (invalid credentials)
     */
    AUTHENTICATION_ERROR,
    
    /**
     * Server-side error from the provider
     */
    SERVER_ERROR,
    
    /**
     * Invalid response from provider (malformed data)
     */
    INVALID_RESPONSE,
    
    /**
     * Location not supported by provider
     */
    UNSUPPORTED_LOCATION,
    
    /**
     * No weather data available for requested parameters
     */
    NO_DATA,
    
    /**
     * Data is stale and cannot be used
     */
    STALE_DATA,
    
    /**
     * Error during parsing of response
     */
    PARSE_ERROR,
    
    /**
     * Unknown error occurred
     */
    UNKNOWN_ERROR
}