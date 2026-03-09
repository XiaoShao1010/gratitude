# Project Overview: Gratitude - MaixCam Obstacle Detection

This project, "Gratitude," implements the obstacle detection and spatial interaction component for a mobile application designed for visually impaired persons. It utilizes the **MaixCam (K230)** hardware platform to provide real-time environment perception and interactive guidance.

## Core Technologies
- **Hardware**: MaixCam (Dual-core RISC-V K230 @ 1.6GHz + 800MHz).
- **Operating System**: MaixPy v3 (Python-based environment).
- **AI Model**: **YOLO11-n (Nano)**, optimized for the K230 KPU.
- **Perception**: Real-time detection of dynamic (pedestrians, vehicles) and static (poles, hydrants, benches) obstacles.
- **Interaction**: Spatial positioning and monocular distance estimation for object localization.

## Architecture & Logic
- **Distributed Architecture**:
  - **MaixCam (Edge Node)**: Performs high-speed YOLO11 detection and calculates spatial coordinates. Acts as a `Slave` using the standard binary protocol (TCP 5555).
  - **Raspberry Pi (Central Controller)**: Orchestrates feedback and user interaction. Acts as a `Master`, parsing binary frames with CRC16 and providing TTS via `espeak`.
- **Safety Mode (Obstacle Detection)**: Continuous background monitoring with priority alerts pushed to RPi.
- **Interaction Mode (Object Search)**: RPi sends search commands; MaixCam responds with directional guidance.

## Key Files & Directory Structure
- `main.py`: MaixCam entry point.
- `gratitude/`: MaixCam core logic package.
- `rpi_service/`: Raspberry Pi standalone project folder.
  - `main.py`: RPi entry point (Master protocol logic).
  - `README.md`: Deployment guide for RPi.
- `docs/`: Technical documentation and project guidelines.
- `models/`: Directory for `.kmodel` or `.mud` files.

## Building and Running
### Prerequisites
- MaixCam hardware with MaixPy v3 firmware.
- YOLO11 model file located in the configured path (default: `/root/models/`).

### Commands
- **Run the Application**:
  ```bash
  python3 main.py
  ```
- **Packaging**:
  ```bash
  pip install build
  python -m build
  ```

## Development Conventions
- **Naming**: Use descriptive `snake_case` for Python variables and functions.
- **Hardware Interaction**: Exclusively use the `maix` library for hardware-accelerated tasks.
- **Performance**: Maintain high frame rates (30-40 FPS) by minimizing heavy CPU operations in the main loop.

### Commit Message Standards
All commits must follow these prefixes:
- `feat`: New feature (e.g., a new interaction mode)
- `fix`: Bug fix (e.g., correcting distance calculation)
- `refactor`: Code restructuring without changing behavior
- `docs`: Documentation updates
- `test`: Adding or updating tests
- `chore`: Build system changes or tool configurations
