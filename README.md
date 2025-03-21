# FileUploadDownloadManager
This solution incorporates all features and use cases discussed, including:

Chunked downloads.

Pause, resume, cancel functionality.

Retrying failed chunks.

Persistent state for device restarts.

Dynamic network adaptation.

Random chunk order handling.

Chunk integrity verification.

Concurrency control for optimal performance.

Support for multiple downloads simultaneously.

Features Included
Chunk Management: Tracks each chunk's status (pending, downloading, completed, failed).

Retry Mechanism: Automatically retries failed chunks with limited retries.

Pause, Resume, Cancel: Fully supports stopping and continuing downloads.

Persistent State: Saves chunk progress for resuming after interruptions.

Random Chunk Handling: Handles random server responses by assigning chunks to correct offsets.

Network Awareness: Pauses downloads if no network is available and resumes when restored.

Simultaneous Downloads: Handles multiple downloads efficiently.

Progress Updates: Reports real-time progress for individual chunks and overall file.
