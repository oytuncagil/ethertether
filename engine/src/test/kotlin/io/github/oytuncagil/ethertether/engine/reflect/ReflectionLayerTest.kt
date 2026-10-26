// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine.reflect

import io.github.oytuncagil.ethertether.engine.ErroredIface
import io.github.oytuncagil.ethertether.engine.PortRequest
import io.github.oytuncagil.ethertether.engine.TetherIface
import io.github.oytuncagil.ethertether.engine.TetheringApiMissing
import io.github.oytuncagil.ethertether.engine.TetheringConstants
import io.github.oytuncagil.ethertether.model.Ipv4Address
import io.github.oytuncagil.ethertether.model.StaticAddresses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReflectionLayerTest {
    private val filler = ArgumentFiller(FAKE_NAMES, "com.android.shell", 5)
    private val listener = object : FakeIntListener {}
    private val parcel = FakeRequestParcel(emptyMap())

    @Test
    fun androidElevenShapeIsFilledWithoutTag() {
        val connector = Connector11()
        val methods = ConnectorMethods(Connector11::class.java, FAKE_NAMES)
        methods.start.invoke(connector, *filler.fill(methods.start, listener, parcel))
        methods.stop.invoke(connector, *filler.fill(methods.stop, listener))
        assertEquals(listOf(parcel, "com.android.shell", listener), connector.calls[0])
        assertEquals(listOf(5, "com.android.shell", listener), connector.calls[1])
        assertNull("Android 11 has no unregister in this fake", methods.unregister)
    }

    @Test
    fun androidTwelveShapeGetsANullAttributionTag() {
        val connector = Connector12()
        val methods = ConnectorMethods(Connector12::class.java, FAKE_NAMES)
        methods.start.invoke(connector, *filler.fill(methods.start, listener, parcel))
        assertEquals(listOf(parcel, "com.android.shell", null, listener), connector.calls.single())
        assertTrue(methods.register != null && methods.unregister != null)
    }

    @Test
    fun androidSixteenStopIsNotStopTetheringRequest() {
        val connector = Connector16()
        val methods = ConnectorMethods(Connector16::class.java, FAKE_NAMES)
        methods.stop.invoke(connector, *filler.fill(methods.stop, listener))
        assertEquals(listOf("stopTethering"), connector.calls)
    }

    @Test
    fun unknownParameterTypesAreReportedNotGuessed() {
        val methods = ConnectorMethods(ConnectorUnknown::class.java, FAKE_NAMES)
        val error = assertThrows(TetheringApiMissing::class.java) { filler.fill(methods.start, listener, parcel) }
        assertTrue(error.what.contains("long"))
    }

    @Test
    fun missingMethodsAreReportedByName() {
        val error = assertThrows(TetheringApiMissing::class.java) { ConnectorMethods(ConnectorBroken::class.java, FAKE_NAMES) }
        assertTrue(error.what.contains("startTethering"))
    }

    @Test
    fun describeListsTheResolvedSignatures() {
        val lines = ConnectorMethods(Connector12::class.java, FAKE_NAMES).describe()
        assertEquals("startTethering(FakeRequestParcel, String, String, FakeIntListener)", lines[0])
    }

    private val addresses = StaticAddresses(Ipv4Address.parse("169.254.0.42")!!, Ipv4Address.parse("169.254.0.1")!!, 16)

    private fun factory(builder: Class<*>) = RequestFactory(builder) { address, prefix -> FakeLinkAddress(address.toString(), prefix) }

    @Test
    fun requestIsBuiltWithTheDevicesBuilder() {
        val parcel = factory(Builder12::class.java).parcel(PortRequest(addresses, TetheringConstants.CONNECTIVITY_SCOPE_LOCAL, true)) as FakeRequestParcel
        assertEquals(5, parcel.fields["type"])
        assertEquals(FakeLinkAddress("169.254.0.42", 16), parcel.fields["local"])
        assertEquals(FakeLinkAddress("169.254.0.1", 16), parcel.fields["client"])
        assertEquals(true, parcel.fields["exempt"])
        assertEquals(false, parcel.fields["showUi"])
        assertEquals(2, parcel.fields["scope"])
    }

    @Test
    fun dhcpRequestSetsNoAddresses() {
        val parcel = factory(Builder12::class.java).parcel(PortRequest(null, null, false)) as FakeRequestParcel
        assertFalse(parcel.fields.containsKey("local"))
        assertFalse(parcel.fields.containsKey("scope"))
    }

    @Test
    fun androidElevenBuilderHasNoScope() {
        val factory = factory(Builder11::class.java)
        assertFalse(factory.supportsConnectivityScope)
        assertTrue(factory(Builder12::class.java).supportsConnectivityScope)
        assertThrows(TetheringApiMissing::class.java) { factory.parcel(PortRequest(addresses, 2, true)) }
        val parcel = factory.parcel(PortRequest(addresses, null, true)) as FakeRequestParcel
        assertFalse(parcel.fields.containsKey("scope"))
    }

    @Test
    fun builderExceptionsReachTheCallerUnwrapped() {
        val bad = factory(Builder12::class.java)
        val mismatched = RequestFactory(Builder12::class.java) { address, _ -> FakeLinkAddress(address.toString(), if (address.toString().endsWith(".1")) 24 else 16) }
        assertThrows(IllegalArgumentException::class.java) { mismatched.parcel(PortRequest(addresses, null, true)) }
        assertTrue(bad.describe().all { it.endsWith("found") })
    }

    @Test
    fun missingBuilderSettersAreReported() {
        assertThrows(TetheringApiMissing::class.java) { factory(BuilderBroken::class.java).parcel(PortRequest(addresses, null, true)) }
    }

    @Test
    fun androidElevenStatesAreNames() {
        val states = StatesParser.parse(StatesParcel11())
        assertEquals(listOf(TetherIface("eth0", null), TetherIface("rndis0", null)), states.tethered)
        assertEquals(listOf(ErroredIface(TetherIface("eth2", null), 10)), states.errored)
        assertEquals("eth0", states.ethernetServing(30)?.first?.name)
    }

    @Test
    fun androidTwelveStatesAreTypedInterfaces() {
        val states = StatesParser.parse(StatesParcel12())
        assertEquals(listOf(TetherIface("eth0", 5)), states.localOnly)
        assertTrue(states.errored.isEmpty())
    }

    @Test
    fun callbackStartedParcelHoldsTheStates() {
        assertEquals(listOf(TetherIface("eth0", 5)), StatesParser.parseStarted(StartedParcel(StatesParcel12()))?.localOnly)
        assertNull(StatesParser.parseStarted(StartedParcel(null)))
    }

    @Test
    fun missingStateFieldsAreReported() {
        assertThrows(TetheringApiMissing::class.java) { StatesParser.parse(Any()) }
    }

    @Test
    fun fieldDumpShowsPublicFields() {
        val dump = FieldDump.of(StatesParcel11())
        assertTrue(dump, dump.startsWith("StatesParcel11{") && dump.contains("tetheredList=[eth0, rndis0]") && dump.contains("lastErrorList=[10]"))
        assertEquals("toString when there are no fields", "plain", FieldDump.of("plain"))
    }
}
