# Ants Pattern Documentation

## Overview

The Ants pattern simulates intelligent ant pathfinding using a single seeker ant that finds a path between start and target points, then spawns following ants that travel along the discovered path. The pattern supports both cube and cylinder rendering with dynamic coordinate systems that adapt to the selected shape.

## Architecture

### Dynamic Coordinate System

The pattern uses a shape-aware coordinate system that automatically adapts based on the selected shape:

**Cube Mode** (Default):
```
Ring 0 (top):    [Front: 0-49] [Right: 50-99] [Back: 100-149] [Left: 150-199]
Ring 1:          [Front: 0-49] [Right: 50-99] [Back: 100-149] [Left: 150-199]
...
Ring 44 (bottom): [Front: 0-49] [Right: 50-99] [Back: 100-149] [Left: 150-199]
```
- **Ring Width**: 200 pixels (4 faces × 50 pixels each)
- **Ring Height**: 45 rings total
- **Door Position**: Column 20 on each face (every 50 pixels)

**Cylinder Mode**:
```
Ring 0 (top):    [0-119] (circular)
Ring 1:          [0-119] (circular)
...
Ring 42 (bottom): [0-119] (circular)
```
- **Ring Width**: 120 pixels (circular)
- **Ring Height**: 43 rings total
- **Door Position**: Column 10 (repeats every 30 pixels)

- **Door Avoidance**: Automatically detects and avoids door areas for both shapes

### Three-Phase Behavior

1. **Pathfinding Phase**: 
   - Single seeker ant starts from controllable start position
   - Intelligently navigates toward target with strong directional bias (80%) and small random variation (20%)
   - Creates visible trail showing path as it explores
   - Reaches target and immediately begins return journey

2. **Return Phase**:
   - Seeker ant returns home using similar intelligent navigation
   - Creates return trail segments marked with direction information
   - Completes when ant reaches within 2 pixels of home

3. **Ant Flow Phase**:
   - Initial ants spawn at very low time-based rate to seed the colony
   - When an ant completes its round trip (returns home), it spawns new ants based on quantity parameter
   - This creates self-sustaining colony behavior where successful foraging trips encourage more ants
   - Ants flow bidirectionally: forward (to target) and return (to start)
   - Each ant maintains consistent color throughout entire journey

## Key Classes

### `SeekerAnt`
- **Intelligence**: Uses moderate directional bias (60%) toward target/home with increased random variation (40%)
- **Natural Movement**: Creates more windy, organic paths that feel naturally wandering
- **Path Creation**: Builds trail of `AntSegment` objects as it moves
- **States**: `isGoingToTarget()` and `returningHome` track current direction
- **Door Avoidance**: Automatically redirects upward when encountering door areas

### `MovingAnt`
- **Position**: 0.0-2.0 range (0-1 = to target, 1-2 = returning)
- **Color**: Fixed white color for all ants
- **Path Following**: Uses `getPathPosition()` to convert 0-2 range to 0-1 path coordinates
- **Movement**: Continuous linear progression along discovered path

### `AntSegment`
- **Coordinates**: X,Y position in ring-based coordinate system
- **Direction**: `goingToTarget` boolean stores whether segment was created going to target or returning
- **Rendering**: Direction information used for surface-specific rendering (exterior/interior)

## Parameters

### Start/Target Controls
- **Start X**: Horizontal start position (0.0-1.0, 0=left, 1=right)
- **Start Y**: Vertical start position (0.0-1.0, 0=top, 1=bottom)
- **Target X**: Horizontal target position (0.0-1.0, 0=left, 1=right)
- **Target Y**: Vertical target position (0.0-1.0, 0=top, 1=bottom)
- **Debug Start**: Shows green cross at start location
- **Debug Target**: Shows red cross at target location

### Movement Controls
- **Speed**: Overall movement and spawn rate (0.1-2.0)
- **Spacing**: Distance between path segments (0.5-3.0)
- **Max Change**: Random direction variation per step (0.1-1.0)
- **Attraction**: How strongly seeker ant is drawn to target (0.0-1.0)

### Visual Controls
- **Size**: Ant circular radius size (0.5-4.0)
- **Quantity**: Number of ants spawned when an ant returns home (0.1-3.0)
- **Clear**: Momentary button to reset entire pattern

### Rendering Controls
- **Forward**: Where to render forward path (Exterior/Interior/Both) - Default: Exterior
- **Return**: Where to render return path (Exterior/Interior/Both) - Default: Interior
- **Lane Sep**: Toggle lane separation for opposing directions
- **Lane Dist**: Distance between opposing lanes (0.5-8.0)

### Shape Controls
- **Shape**: Choose between Cube and Cylinder rendering (Cube/Cylinder) - Default: Cube

### Advanced Controls
- **Explorers**: Rate of explorer ants seeking new paths (0.0-0.5)

## Technical Implementation

### Intelligent Pathfinding Algorithm
1. **Target Navigation**: Calculate direction to target using `atan2(dy, dx)`
2. **Natural Movement**: 60% directional bias + 40% random variation for windy, organic pathfinding
3. **Door Avoidance**: Automatically redirects upward when encountering door areas
4. **Segment Creation**: Adds path segments every few pixels to create visible trail
5. **Direction Tracking**: Each segment stores whether it was created going to target or returning

### Surface-Specific Rendering
- **Forward Path**: Renders on exterior surface by default (configurable)
- **Return Path**: Renders on interior surface by default (configurable)
- **Direction-Based**: Each segment uses stored direction info for proper surface selection
- **Following Ants**: Respect same surface rules as original path segments

### Spawn-on-Return Mechanism
- **Event-Based Spawning**: Primary spawning occurs when ants complete their round trip (position >= 2.0)
- **Random Delay**: Each returning ant spawns new ants after a random delay (0-2 seconds)
- **Quantity Control**: Each returning ant spawns 1-3 new ants based on quantity parameter
- **Self-Sustaining Colony**: Successful foraging trips encourage more ants to follow the path
- **Reduced Time-Based**: Initial seeding uses low-rate time-based spawning, then switches to return-based

### Bidirectional Flow
- **Single Path**: Discovered path supports ants moving in both directions
- **Position Mapping**: 0.0-1.0 = forward journey, 1.0-2.0 = return journey
- **Lane Separation**: Optional visual distinction between opposing flows
- **Consistent Color**: All ants maintain white color throughout journey

### Size Rendering
- **Circular Pattern**: Uses circular pixel pattern around each ant position
- **Radius Calculation**: Radius directly from size parameter (no scaling)
- **Center + Surrounding**: Draws center pixel plus surrounding pixels within size distance

## Usage Patterns

### Directional Visualization
- **Forward Path**: Set to "Exterior" to see pathfinding on outer surface
- **Return Path**: Set to "Interior" to see return journey on inner surface
- **Both Surfaces**: Set either to "Both" for maximum visibility

### Dynamic Target Changes
- **Path Reset**: Moving target/start triggers complete pathfinding restart
- **Seeker Respawn**: New seeker ant spawns and finds new path
- **Ant Clearing**: Existing following ants are cleared during path reset

### Start/Target Positioning
- **Controllable Positions**: Both start and target are controllable via parameters
- **Debug Visualization**: Enable debug markers to see exact positions
- **Coordinate System**: Uses normalized 0.0-1.0 coordinates across ring system

## Performance Considerations

- **Single Seeker**: Uses one pathfinding ant instead of multiple scouts
- **Efficient Navigation**: Strong directional bias reduces wandering
- **Segment Storage**: Path stored as discrete segments with direction info
- **Surface Rendering**: Direction-based rendering reduces overdraw
- **Ring Coordinates**: Uses efficient ring-based coordinate system

## Recent Updates

- **Single Seeker**: Replaced multiple scout ants with single intelligent seeker
- **Directional Rendering**: Added surface-specific rendering based on path direction
- **Intelligent Navigation**: 80% target bias with 20% variation for natural movement
- **Path Filtering**: Only forward path segments used for following ants (no back-and-forth)
- **Door Avoidance**: Automatic detection and avoidance of door areas
- **Cylinder Support**: Added full cylinder rendering support with dynamic coordinate system
- **Shape-Aware Door Detection**: Different door positions for cube (every 50px) vs cylinder (every 30px)