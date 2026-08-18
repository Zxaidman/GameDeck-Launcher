package com.gamedeck.core.configuration

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for ConfigurationValidator.
 */
class ConfigurationValidatorTest {

    private val validator = ConfigurationValidator()

    @Test
    fun `valid document passes validation`() {
        val document = ConfigurationDocument(
            schemaVersion = 1,
            type = "controller-layout",
            id = "builtin.xbox.default",
            name = "Xbox Default"
        )
        val result = validator.validate(document)
        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `invalid schema version fails validation`() {
        val document = ConfigurationDocument(
            schemaVersion = 0,
            type = "controller-layout",
            id = "builtin.xbox.default",
            name = "Xbox Default"
        )
        val result = validator.validate(document)
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `unsupported type fails validation`() {
        val document = ConfigurationDocument(
            schemaVersion = 1,
            type = "unknown-type",
            id = "builtin.xbox.default",
            name = "Xbox Default"
        )
        val result = validator.validate(document)
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `invalid id prefix fails validation`() {
        val document = ConfigurationDocument(
            schemaVersion = 1,
            type = "controller-layout",
            id = "invalid.id",
            name = "Xbox Default"
        )
        val result = validator.validate(document)
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `blank name fails validation`() {
        val document = ConfigurationDocument(
            schemaVersion = 1,
            type = "controller-layout",
            id = "builtin.xbox.default",
            name = ""
        )
        val result = validator.validate(document)
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `user id prefix passes validation`() {
        val document = ConfigurationDocument(
            schemaVersion = 1,
            type = "controller-layout",
            id = "user.my-layout",
            name = "My Layout"
        )
        val result = validator.validate(document)
        assertTrue(result is ValidationResult.Valid)
    }
}