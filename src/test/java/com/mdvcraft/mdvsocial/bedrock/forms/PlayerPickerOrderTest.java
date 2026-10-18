package com.mdvcraft.mdvsocial.bedrock.forms;

import com.mdvcraft.mdvsocial.bedrock.forms.PlayerPickerForms.Candidate;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerPickerOrderTest {

    private static Candidate candidate(String name, boolean friend) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        return new Candidate(player, friend);
    }

    @Test
    void friendsComeFirstThenAlphabetical() {
        List<Candidate> list = new ArrayList<>(List.of(
                candidate("zeta", false),
                candidate("Bruno", true),
                candidate("ana", false),
                candidate("alba", true)));
        list.sort(PlayerPickerForms.ORDER);
        assertEquals(List.of("alba", "Bruno", "ana", "zeta"),
                list.stream().map(c -> c.player().getName()).toList());
    }
}
