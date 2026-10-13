package com.aicompanion.debug;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.spi.ExtendedLogger;
import org.apache.logging.log4j.spi.ExtendedLoggerWrapper;

public final class TracingLogger extends ExtendedLoggerWrapper {
   private TracingLogger(ExtendedLogger var1) {
      super(var1, var1.getName(), var1.getMessageFactory());
   }

   public static Logger wrap(Logger var0) {
      try {
         if (var0 instanceof ExtendedLogger var1) {
            return new TracingLogger(var1);
         }
      } catch (Throwable var2) {
      }

      return var0;
   }

   public static Logger create(String var0) {
      return wrap(LogManager.getLogger(var0));
   }

   private static boolean traced(Level var0) {
      return var0 != null && var0.intLevel() <= Level.DEBUG.intLevel() && EsclavoTrace.isActive();
   }

   public boolean isEnabled(Level var1, Marker var2, Message var3, Throwable var4) {
      return traced(var1) || super.isEnabled(var1, var2, var3, var4);
   }

   public boolean isEnabled(Level var1, Marker var2, CharSequence var3, Throwable var4) {
      return traced(var1) || super.isEnabled(var1, var2, var3, var4);
   }

   public boolean isEnabled(Level var1, Marker var2, Object var3, Throwable var4) {
      return traced(var1) || super.isEnabled(var1, var2, var3, var4);
   }

   public boolean isEnabled(Level var1, Marker var2, String var3) {
      return traced(var1) || super.isEnabled(var1, var2, var3);
   }

   public boolean isEnabled(Level var1, Marker var2, String var3, Object... var4) {
      return traced(var1) || super.isEnabled(var1, var2, var3, var4);
   }

   public boolean isEnabled(Level var1, Marker var2, String var3, Throwable var4) {
      return traced(var1) || super.isEnabled(var1, var2, var3, var4);
   }

   public boolean isEnabled(Level var1, Marker var2, String var3, Object var4) {
      return traced(var1) || super.isEnabled(var1, var2, var3, var4);
   }

   public boolean isEnabled(Level var1, Marker var2, String var3, Object var4, Object var5) {
      return traced(var1) || super.isEnabled(var1, var2, var3, var4, var5);
   }

   public boolean isEnabled(Level var1, Marker var2, String var3, Object var4, Object var5, Object var6) {
      return traced(var1) || super.isEnabled(var1, var2, var3, var4, var5, var6);
   }

   public boolean isEnabled(Level var1, Marker var2, String var3, Object var4, Object var5, Object var6, Object var7) {
      return traced(var1) || super.isEnabled(var1, var2, var3, var4, var5, var6, var7);
   }

   public boolean isEnabled(Level var1, Marker var2, String var3, Object var4, Object var5, Object var6, Object var7, Object var8) {
      return traced(var1) || super.isEnabled(var1, var2, var3, var4, var5, var6, var7, var8);
   }

   public boolean isEnabled(Level var1, Marker var2, String var3, Object var4, Object var5, Object var6, Object var7, Object var8, Object var9) {
      return traced(var1) || super.isEnabled(var1, var2, var3, var4, var5, var6, var7, var8, var9);
   }

   public boolean isEnabled(Level var1, Marker var2, String var3, Object var4, Object var5, Object var6, Object var7, Object var8, Object var9, Object var10) {
      return traced(var1) || super.isEnabled(var1, var2, var3, var4, var5, var6, var7, var8, var9, var10);
   }

   public boolean isEnabled(
      Level var1, Marker var2, String var3, Object var4, Object var5, Object var6, Object var7, Object var8, Object var9, Object var10, Object var11
   ) {
      return traced(var1) || super.isEnabled(var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11);
   }

   public boolean isEnabled(
      Level var1,
      Marker var2,
      String var3,
      Object var4,
      Object var5,
      Object var6,
      Object var7,
      Object var8,
      Object var9,
      Object var10,
      Object var11,
      Object var12
   ) {
      return traced(var1) || super.isEnabled(var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12);
   }

   public boolean isEnabled(
      Level var1,
      Marker var2,
      String var3,
      Object var4,
      Object var5,
      Object var6,
      Object var7,
      Object var8,
      Object var9,
      Object var10,
      Object var11,
      Object var12,
      Object var13
   ) {
      return traced(var1) || super.isEnabled(var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13);
   }

   public void logMessage(String var1, Level var2, Marker var3, Message var4, Throwable var5) {
      try {
         String var6 = var4 != null ? var4.getFormattedMessage() : "";
         if (var5 != null) {
            var6 = var6 + " | excepcion: " + var5;
         }

         EsclavoTrace.log("LOG-" + var2.name(), var6);
      } catch (Throwable var8) {
      }

      try {
         if (this.logger.isEnabled(var2, var3, var4, var5)) {
            super.logMessage(var1, var2, var3, var4, var5);
         }
      } catch (Throwable var7) {
      }
   }
}
