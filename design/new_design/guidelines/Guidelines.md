# Tablet Screen Recording App - Design Guidelines

## Aesthetic Stance: Kinetic
The application follows a "Kinetic" aesthetic: motion is primary, heavily leveraging a sleek dark mode with high contrast light highlights. The interface should feel like professional broadcast equipment or Apple product reveals—precise, responsive, and purposeful.

## Typography
- **Primary / UI Typeface:** Inter (Humanist Sans) - Clean, neutral, extremely readable at all tablet viewing distances.
- **Weights:** Regular (400) for body, Medium (500) for interactive elements, Semi-bold (600) for section headers.

## Color Palette (Design Tokens)
- **Background:** Deep almost-black (`#09090b`) to immerse the user and reduce glare.
- **Card/Surface:** Slightly elevated dark gray (`#18181b`) for modular panels and settings cards.
- **Primary / Accent:** High-contrast white (`#fafafa`) or an energetic recording red (`#ef4444`) for active recording states.
- **Foreground:** Off-white (`#fafafa`) for maximum legibility on dark backgrounds.
- **Muted:** Gray (`#a1a1aa`) for secondary information, timestamps, and metadata.

## Layout & Responsiveness
- **Tablet First:** The design prioritizes tablet viewports (e.g., iPad, Galaxy Tab).
- **Orientation Fluidity:** Uses Tailwind's `portrait:` and `landscape:` modifiers.
  - **Landscape:** Exposes a dedicated side navigation bar (Sidebar).
  - **Portrait:** Replaces the sidebar with a Top bar and a Bottom Navigation Bar to maximize horizontal space.
- **Safe Areas:** Padding must accommodate grip areas on tablet edges (min 24px-32px padding).

## Interactive States (Feedback & Selection)
To ensure the interface feels alive and responsive ("Kinetic"):
- **Selection Highlights:** When clicking major options (e.g., "Full Screen" or "Specific App" in the Home screen), the component acquires a vibrant primary colored border (`ring-1 ring-primary`) and a subtle colored shadow (`shadow-primary/10`).
- **Toggles (Switches):** Boolean settings (e.g., System Audio, Face Cam) feature an interactive iOS-style toggle. The knob transitions smoothly (`translate-x-4`) and the background color shifts to the primary color when active.
- **Click Actions:** All buttons and interactive cards feature a subtle active scaling effect (`active:scale-95`) and hover background color adjustments (`hover:bg-secondary/30`).

## Core UI Components
1. **Speed Dial (Expandable FAB):**
   - Positioned fixed (bottom-right) for easy thumb access. Position dynamically adjusts based on orientation (`portrait` vs `landscape`) so it does not overlap the bottom navigation bar.
   - Core states: Idle and Expanded (with smooth stagger-fade-in animation for child actions).
2. **Library (Recording List & Trash):**
   - **Selection Mode:** Users can toggle a "Select" mode to perform multi-select actions. Unselected items dim to `opacity-50`, while selected items receive a primary border and checkmark.
   - **Trash Management:** Deleted items move to a "Trash" screen with options to "Restore" or "Delete Forever". Trashed video thumbnails use a `grayscale` effect.
3. **Video Player UI:**
   - Overlay that covers the whole screen on selection.
   - Center Controls: Large Primary colored Play/Pause with 10-second skip forward/backward buttons.
   - Bottom Controls: Playback speed adjuster (`1x`, `1.5x`, `2x`...), fake progress bar, and settings.
   - **Auto-hide:** Controls gracefully fade away (`opacity-0`) after 3 seconds of inactivity while playing, returning instantly on tap.
4. **Settings Panels:**
   - Grouped into distinct rounded cards with interactive list items (chevron arrows for navigation, toggles for boolean states).

## Micro-details
- Transitions should be fast and snappy (150ms-300ms duration, ease-out timing).
- Ensure buttons give immediate visual feedback.