# Verdant (Fabric, Minecraft 1.21.11)

Client-side mod with a forest-green click GUI and two modules:

- **Storage ESP** - highlights chests, trapped chests, ender chests, hoppers, shulker boxes (and barrels, off by default)
- **Spawn Finder** - highlights mob spawners

Press **Right Shift** in a world to open the menu (rebind in Options > Controls > Key Binds > Verdant).

## Customization
- Per module: range, through-walls, outline width/opacity, fill on/off + opacity, distance fade, rainbow mode + speed
- Per block type: on/off and a full hue/saturation/brightness color editor with presets and "apply to all"
- Settings save to `config/verdant.properties`

## Needs
Fabric Loader, Fabric API for 1.21.11, Java 21. Install only on your own client (not on the server).

## Build
`gradle build` (Gradle 9.2+). The jar is in `build/libs/` - use the one without `-sources`.
