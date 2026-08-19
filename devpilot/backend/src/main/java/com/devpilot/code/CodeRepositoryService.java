package com.devpilot.code;

import com.devpilot.code.model.ListFilesRequest;
import com.devpilot.code.model.ListFilesResult;
import com.devpilot.code.model.ReadCodeFileRequest;
import com.devpilot.code.model.ReadCodeFileResult;
import com.devpilot.code.model.SearchCodeRequest;
import com.devpilot.code.model.SearchCodeResult;

/**
 * Read-only access to the source repository a project is bound to.
 *
 * <p>This is the capability definition: agents and tools depend on it, never on a concrete
 * repository implementation. Swapping the local directory for a hosted Git service later means
 * writing another provider, not changing a prompt or a tool schema.
 *
 * <p>Every operation is confined to the repository root of the named project. An implementation
 * must refuse paths that escape the root, files on the sensitive-file blacklist and anything that
 * is not a readable text file of an allowed type, by raising {@link RepositoryAccessException}.
 */
public interface CodeRepositoryService {

    /**
     * Lists files under a directory.
     *
     * @param request directory, depth and limit
     * @return repository-relative paths
     * @throws RepositoryAccessException when the directory cannot be read
     */
    ListFilesResult listFiles(ListFilesRequest request);

    /**
     * Searches the text of the repository.
     *
     * @param request keyword, file pattern and limit
     * @return matching lines with surrounding context
     * @throws RepositoryAccessException when the repository cannot be read
     */
    SearchCodeResult searchCode(SearchCodeRequest request);

    /**
     * Reads a line range of one file.
     *
     * @param request file and line range
     * @return the requested lines
     * @throws RepositoryAccessException when the file cannot be read
     */
    ReadCodeFileResult readFile(ReadCodeFileRequest request);
}
