package com.streamx.blueprints.externalresources.testutils;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * When your test changes configuration properties, and they may be cached by beans - use this
 * profile to force recreating all beans for next tests
 */
public class ForceRecreatingBeans implements QuarkusTestProfile {

}
