This folder should contain the optimized torchscript model for PyTorch Mobile.

Steps to export:
1. On your development machine with a Python environment that has the project dependencies, run:
   ```bash
   python3 backend/export_model_mobile.py
   ```
   A model file `ekyc_model_32.pt` will be created in the backend directory.

2. Copy or move that model into the Android app assets folder with the name `ekyc_model.pt`:
   ```bash
   mkdir -p android/app/src/main/assets
   cp backend/ekyc_model_32.pt android/app/src/main/assets/ekyc_model.pt
   ```

3. Rebuild the Android app.

Notes:
- This file is a placeholder; for security reasons do not commit the model weights if they contain proprietary data.
- If you want to debug locally without a real model, add a test model with the same input shapes.
