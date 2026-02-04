# PD_Update_Patcher

## Basic Information
- **Exe Name**: `PD_Update_Patcher.exe`
- **Project Path**: `../sample_data/photodemon/Support/Update Patcher 2.0/PD_Update_Patcher.vbp`

## Components
### Forms (1)
- `frmPatch.frm`

### Modules (11)
- `modSupport.bas`
- `modMain.bas`
- `Compression.bas`
- `Files.bas`
- `OS.bas`
- `Placeholder.bas`
- `Plugin_lz4.bas`
- `Plugin_ZLib.bas`
- `Plugin_zstd.bas`
- `Strings.bas`
- `VB_Hacks.bas`
## Connections
### Outbound Calls
This project calls the following external executables:

| Target | Source File | Line | Content |
| :--- | :--- | :---: | :--- |
| [PhotoDemon](PhotoDemon.md) | `frmPatch.frm` | 220 | `'If the process name is "PhotoDemon.exe", note it` |
| [PhotoDemon](PhotoDemon.md) | `frmPatch.frm` | 221 | `If Right$(szExename, Len("PhotoDemon.exe")) = "PhotoDemon.exe" Then` |
| `\PD_Update_Patcher.exe` (Unknown) | `frmPatch.frm` | 307 | `If Strings.StringsNotEqual(newFilename, "\PD_Update_Patcher.exe", True) Then` |
| [PhotoDemon](PhotoDemon.md) | `frmPatch.frm` | 508 | `fileString = "PhotoDemon.exe"` |
| `Shell_Call` (Unknown) | `Files.bas` | 0 | `leftHandCall=[name=[ShellAndWait], procedure=[name=[ShellAndWait]]]` |
| [PhotoDemon](PhotoDemon.md) | `OS.bas` | 621 | `If (InStr(1, tmpString, "PhotoDemon.exe", vbBinaryCompare) = 0) Then dstStringStack.AddString tmpString` |

### Dependency Graph
```mermaid
graph LR
  Current[PD_Update_Patcher]
  Current --> |Calls| PhotoDemon[PhotoDemon]
  Current --> |Calls| PhotoDemon[PhotoDemon]
  Current --> |Calls| PDUpdatePatcherexe[\PD_Update_Patcher.exe]
  Current --> |Calls| PhotoDemon[PhotoDemon]
  Current --> |Calls| ShellCall[Shell_Call]
  Current --> |Calls| PhotoDemon[PhotoDemon]
```
