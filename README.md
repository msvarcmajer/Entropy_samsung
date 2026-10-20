# Entropy Evaluation of Samsung Smartwatch Sensors

This repository contains the source code and datasets used in the study  
*"Entropy Evaluation of Smartwatch Sensors as a Source for Cryptographic Key Generation in Blockchain Applications"* (Miljenko Švarcmajer et al., published in MDPI *Sensors*, 2025).

## Repository Contents
- `/csv_data/` – Raw sensor data collected in still and shake modes (CSV format).
- `/app_source/` – Wear OS application source code for entropy acquisition.
- `LICENSE` – Licensing terms (Apache 2.0).

## Usage
1. Clone the repository:  
   git clone https://github.com/msvarcmajer/Entropy_samsung.git
   
2. ### CSV Data Description
- **0907_still_ready.csv** – still-mode measurements. Each row represents one acquisition window (2.2 s), including entropy-ready numerical vectors derived from selected sensors.
- **0907_shake_ready.csv** – shake-mode measurements, structured identically to still-mode data.

3. The Wear OS application can be compiled in Android Studio (API level 30+) and installed on compatible smartwatches (e.g., Samsung Galaxy Watch 7).

### Citation
If you use this code or dataset, please cite:

> Švarcmajer, M.; Köhler, M.; Krpić, Z.; Lukić, I. Entropy Evaluation of Smartwatch Sensors as a Source for Cryptographic Key Generation in Blockchain Applications. *Sensors* **2025**, *25*(17), 5298. https://doi.org/10.3390/s25175298

BibTeX and APA formats are available via the **"Cite this repository"** button on the right.

#### Contact
Using this in your research or a product? I'd be glad to hear about it: miljenko.svarcmajer@ferit.hr
