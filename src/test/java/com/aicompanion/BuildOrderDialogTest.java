package com.aicompanion;

import com.aicompanion.ai.building.BuildOrderDialog;
import com.aicompanion.ai.building.HouseSpec;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Interactive house dialogue: only missing parameters are asked, answers are parsed correctly. */
public class BuildOrderDialogTest {

    @Test
    public void detectsHouseRequestsButNotGoingHome() {
        assertTrue(BuildOrderDialog.isHouseRequest("construyeme una casa de madera"));
        assertTrue(BuildOrderDialog.isHouseRequest("hazme una casa"));
        assertFalse(BuildOrderDialog.isHouseRequest("vuelve a casa"));
        assertFalse(BuildOrderDialog.isHouseRequest("construye una torre"));
    }

    @Test
    public void onlyMissingParametersAreAsked() {
        BuildOrderDialog d = new BuildOrderDialog();
        d.absorb("construyeme una casa grande de piedra con 2 pisos", null);
        HouseSpec s = d.getSpec();
        assertEquals(HouseSpec.Material.STONE, s.walls());
        assertEquals(9, s.widthOrDefault());
        assertEquals(2, s.floorsOrDefault());
        // stone house: no wood question, size and floors known -> windows is next
        assertEquals(BuildOrderDialog.Step.WINDOWS, d.nextMissing());

        assertTrue(d.absorb("si", BuildOrderDialog.Step.WINDOWS));
        assertEquals(BuildOrderDialog.Step.DOOR, d.nextMissing());
        assertTrue(d.absorb("no", BuildOrderDialog.Step.DOOR));
        assertFalse(s.doorOrDefault());
        assertEquals(BuildOrderDialog.Step.LOCATION, d.nextMissing());
        assertTrue(d.absorb("aqui", BuildOrderDialog.Step.LOCATION));
        assertEquals(BuildOrderDialog.LocationMode.HERE_PLAYER, d.getLocationMode());
        assertEquals(BuildOrderDialog.Step.FACING, d.nextMissing());
        assertTrue(d.absorb("norte", BuildOrderDialog.Step.FACING));
        assertEquals(Direction.NORTH, s.facing);
        assertEquals(BuildOrderDialog.Step.CONFIRM, d.nextMissing());
    }

    @Test
    public void woodHouseAsksSpeciesAndAcceptsDefaults() {
        BuildOrderDialog d = new BuildOrderDialog();
        d.absorb("construye una casa", null);
        assertEquals(BuildOrderDialog.Step.MATERIAL, d.nextMissing());
        d.absorb("de madera", BuildOrderDialog.Step.MATERIAL);
        assertEquals(BuildOrderDialog.Step.WOOD, d.nextMissing());
        d.absorb("abeto", BuildOrderDialog.Step.WOOD);
        assertEquals("SPRUCE", d.getSpec().wood);
        assertEquals(BuildOrderDialog.Step.SIZE, d.nextMissing());
        d.absorb("como quieras", BuildOrderDialog.Step.SIZE);
        assertEquals(7, d.getSpec().widthOrDefault());
        d.absorb("2", BuildOrderDialog.Step.FLOORS);
        assertEquals(2, d.getSpec().floorsOrDefault());
    }

    @Test
    public void mixedMaterialsAndThisIsNotEast() {
        BuildOrderDialog d = new BuildOrderDialog();
        d.absorb("construye una casa en este lugar con paredes de piedra y techo de madera", null);
        assertEquals(HouseSpec.Material.STONE, d.getSpec().walls());
        assertEquals(HouseSpec.Material.WOOD, d.getSpec().roof());
        assertNull(d.getSpec().facing, "'este lugar' means 'this place', not east");
    }

    @Test
    public void specSurvivesEncoding() {
        HouseSpec s = new HouseSpec();
        s.wallMaterial = HouseSpec.Material.STONE;
        s.roofMaterial = HouseSpec.Material.WOOD;
        s.wood = "BIRCH";
        s.width = 8;   // normalized to odd
        s.depth = 7;
        s.floors = 3;
        s.windows = false;
        s.door = true;
        s.facing = Direction.WEST;
        String enc = s.encode();
        assertTrue(HouseSpec.isEncoded(enc));
        HouseSpec d = HouseSpec.decode(enc);
        assertEquals(HouseSpec.Material.STONE, d.walls());
        assertEquals(HouseSpec.Material.WOOD, d.roof());
        assertEquals("BIRCH", d.woodOrDefault());
        assertEquals(7, d.depthOrDefault());
        assertEquals(3, d.floorsOrDefault());
        assertFalse(d.windowsOrDefault());
        assertEquals(Direction.WEST, d.facingOrDefault());
    }

    @Test
    public void modifyResetsOnlyThatParameter() {
        BuildOrderDialog d = new BuildOrderDialog();
        d.absorb("construye una casa pequena de madera de roble de 1 piso con ventanas con puerta aqui mirando al sur", null);
        assertEquals(BuildOrderDialog.Step.CONFIRM, d.nextMissing());
        assertEquals(BuildOrderDialog.Step.SIZE, d.resetFromText("modificar tamano"));
        assertEquals(BuildOrderDialog.Step.SIZE, d.nextMissing());
        d.absorb("9x11", BuildOrderDialog.Step.SIZE);
        assertEquals(9, d.getSpec().widthOrDefault());
        assertEquals(11, d.getSpec().depthOrDefault());
        assertEquals(BuildOrderDialog.Step.CONFIRM, d.nextMissing());
    }
}
