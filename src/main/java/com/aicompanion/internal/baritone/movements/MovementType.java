package com.aicompanion.internal.baritone.movements;

public enum MovementType {
   TRAVERSE("Caminar plano"),
   ASCEND("Subir un bloque / Salto"),
   DESCEND("Bajar desnivel"),
   DIAGONAL("Movimiento diagonal"),
   BRIDGE("Colocar bloque puente"),
   TUNNEL("Picar túnel 2 bloques"),
   TUNNEL_DOWN("Picar escalera hacia abajo"),
   TUNNEL_UP("Picar escalera hacia arriba"),
   SWIM("Nadar en agua"),
   FALL("Caída controlada segura"),
   DIG_DOWN("Cavar hacia abajo");

   private final String description;

   MovementType(String nullxx) {
      this.description = nullxx;
   }

   public String getDescription() {
      return this.description;
   }
}
