/*
 * Copyright (c) 2023 OceanBase.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.oceanbase.odc.service.worksheet.domain;

import static com.oceanbase.odc.service.worksheet.constants.WorksheetConstant.NAME_LENGTH_LIMIT;
import static com.oceanbase.odc.service.worksheet.constants.WorksheetConstant.ROOT_PATH_NAME;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.oceanbase.odc.service.worksheet.model.WorksheetLocation;
import com.oceanbase.odc.service.worksheet.model.WorksheetType;
import com.oceanbase.odc.service.worksheet.utils.WorksheetPathUtil;

import lombok.Getter;

/**
 * the path of worksheet
 *
 * @author keyang
 * @date 2024/08/01
 * @since 4.3.2
 */
@Getter
public class Path {
    private static final int ROOT_LEVEL_NUM = 0;
    private static final int WORKSHEETS_LEVEL_NUM = 1;
    private static final int REPOS_LEVEL_NUM = 1;
    private static final int GIT_REPO_LEVEL_NUM = 2;

    List<String> parentPathItems;
    /**
     * Path level, starting from 1, with a folder count by pathItems.size()-1 and a file count by
     * pathItems.size()
     */
    Integer levelNum;
    String name;
    WorksheetType type;
    WorksheetLocation location;

    protected Path() {}

    public static Path root() {
        Path path = new Path();
        path.name = ROOT_PATH_NAME;
        path.type = WorksheetType.ROOT;
        path.location = WorksheetLocation.ROOT;
        path.levelNum = 0;
        path.parentPathItems = new ArrayList<>();
        return path;
    }

    public static Path worksheets() {
        Path path = new Path();
        path.name = WorksheetLocation.WORKSHEETS.getValue();
        path.type = WorksheetType.WORKSHEETS;
        path.location = WorksheetLocation.WORKSHEETS;
        path.levelNum = 1;
        path.parentPathItems = new ArrayList<>();
        return path;
    }

    public static Path repos() {
        Path path = new Path();
        path.name = WorksheetLocation.REPOS.getValue();
        path.type = WorksheetType.REPOS;
        path.location = WorksheetLocation.REPOS;
        path.levelNum = 1;
        path.parentPathItems = new ArrayList<>();
        return path;
    }

    public static Path ofFile(String... items) {
        return new Path(Arrays.stream(items).collect(Collectors.toList()));
    }

    public static Path ofFile(List<String> items) {
        return new Path(items);
    }

    public static Path ofDirectory(String... items) {
        return new Path(WorksheetPathUtil.addSeparatorToItemsEnd(
                Arrays.stream(items).collect(Collectors.toList())));
    }

    public static Path ofDirectory(List<String> items) {
        return new Path(WorksheetPathUtil.addSeparatorToItemsEnd(items));
    }

    public Path(String path) {
        List<String> items = WorksheetPathUtil.splitPathToItems(path);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("invalid path : " + path);
        }
        try {
            initWithItems(items);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid path : " + path);
        }
    }

    public Path(List<String> items) {
        initWithItems(items);
    }

    private void initWithItems(List<String> items) {
        if (CollectionUtils.isEmpty(items)) {
            throw new IllegalArgumentException("items can't be empty");
        }
        Optional<WorksheetLocation> locationOptional = WorksheetPathUtil.getPathLocation(items);
        if (!locationOptional.isPresent()) {
            throw new IllegalArgumentException("invalid items : " + items);
        }
        Optional<WorksheetType> pathTypeOptional = WorksheetPathUtil.getPathType(items);
        if (!pathTypeOptional.isPresent()) {
            throw new IllegalArgumentException("invalid items : " + items);
        }
        Optional<String> standardPathOptional = WorksheetPathUtil.convertItemsToPath(items);
        if (!standardPathOptional.isPresent()) {
            throw new IllegalArgumentException("invalid items : " + items);
        }
        Optional<String> pathNameOptional = WorksheetPathUtil.getPathName(items);
        if (!pathNameOptional.isPresent()) {
            throw new IllegalArgumentException("invalid items : " + items);
        }
        this.name = pathNameOptional.get();
        this.type = pathTypeOptional.get();
        this.location = locationOptional.get();
        this.levelNum = this.isFile() ? items.size() : items.size() - 1;
        this.parentPathItems = levelNum > 0 ? items.subList(0, levelNum - 1) : new ArrayList<>();;
    }

    public String getStandardPath() {
        List<String> pathItems = new ArrayList<>(this.parentPathItems);
        if (!isRoot()) {
            pathItems.add(name);
        }
        if (this.isDirectory()) {
            pathItems = WorksheetPathUtil.addSeparatorToItemsEnd(pathItems);
        }
        return WorksheetPathUtil.convertItemsToPath(pathItems)
                // Under normal circumstances, this exception is not returned.
                // Adding exception handling here is only to ensure program integrity
                // (it is necessary to handle situations where Optional is empty)
                .orElseThrow(() -> new IllegalArgumentException("Invalid Path Object : " + this));
    }

    /**
     * Retrieve the parent path, such as /Worksheets/,/Repos/,/Repos/git/, etc., when current is /
     * return empty
     *
     * @return
     */
    public Optional<Path> getParentPath() {
        if (isRoot()) {
            return Optional.empty();
        }
        return Optional.of(new Path(WorksheetPathUtil.addSeparatorToItemsEnd(this.parentPathItems)));

    }

    public List<Path> getAllNotRootParents() {
        if (isSystemDefine()) {
            return new ArrayList<>();
        }
        List<Path> result = new ArrayList<>();
        for (int i = 0; i < this.levelNum - 1; i++) {
            Path parent = Path.ofDirectory(this.parentPathItems.subList(0, i + 1));
            if (parent.isSystemDefine()) {
                continue;
            }
            result.add(parent);
        }
        return result;

    }

    public boolean isChildOfAny(Path... parents) {
        if (parents == null || parents.length == 0) {
            return false;
        }
        if (this.isRoot()) {
            return false;
        }
        int len = parents.length;
        boolean[] continueFlag = new boolean[len];

        for (int i = 0; i < len; i++) {
            if (parents[i].isRoot()) {
                return true;
            }
            continueFlag[i] = !parents[i].isFile() &&
                    parents[i].getLevelNum() < this.levelNum;
        }
        int index = 0;
        while (index < this.levelNum - 1) {
            String indexName = this.parentPathItems.get(index);
            boolean needContinue = false;
            for (int i = 0; i < len; i++) {
                Path parent = parents[i];
                if (!continueFlag[i]) {
                    continue;
                }
                if (parent.getLevelNum() < index + 1) {
                    continueFlag[i] = false;
                    continue;
                } else if (parent.getLevelNum() == index + 1) {
                    if (Objects.equals(indexName, parent.getName())) {
                        return true;
                    }
                    continueFlag[i] = false;
                    continue;
                }
                if (!StringUtils.equals(indexName, parent.getParentPathItems().get(index))) {
                    continueFlag[i] = false;
                    continue;
                }
                needContinue = true;
            }
            if (!needContinue) {
                break;
            }
            index++;
        }
        return false;
    }

    public boolean isSameParentAtPrevLevel(Path path) {
        if (path.isRoot()) {
            return false;
        }
        if (this.levelNum < path.getLevelNum()) {
            return false;
        }
        return CollectionUtils.isEqualCollection(this.parentPathItems.subList(0, path.getLevelNum() - 1),
                path.getParentPathItems());
    }

    /**
     * get the prefix path at index
     * 
     * @param index
     * @return
     */
    public Path getPathAt(int index) {
        if (index < 0 || index > this.levelNum - 1) {
            throw new IndexOutOfBoundsException(
                    "index is out of bounds,index: " + index + ",levelNum: " + this.levelNum);
        }
        if (index == this.levelNum - 1) {
            return this;
        }
        return Path.ofDirectory(this.parentPathItems.subList(0, index + 1));
    }

    /**
     * strip the prefix path
     * 
     * @param prefixPath
     * @return
     */
    public Optional<String> stripPrefix(Path prefixPath) {
        if (prefixPath.isRoot()) {
            return Optional.of(this.getStandardPath());
        }
        int prefixPathLevelNum = prefixPath.getLevelNum();
        if (this.levelNum <= prefixPathLevelNum) {
            return Optional.empty();
        }
        List<String> items = new ArrayList<>();
        for (int i = prefixPathLevelNum; i < this.parentPathItems.size(); i++) {
            items.add(this.parentPathItems.get(i));
        }
        items.add(this.name);
        if (this.isDirectory()) {
            items = WorksheetPathUtil.addSeparatorToItemsEnd(items);
        }
        return WorksheetPathUtil.convertItemsToPath(items);
    }

    public boolean isRoot() {
        return this.location == WorksheetLocation.ROOT;
    }

    public boolean isWorksheets() {
        return this.location == WorksheetLocation.WORKSHEETS && this.type == WorksheetType.WORKSHEETS;
    }

    public boolean isRepos() {
        return this.location == WorksheetLocation.REPOS && this.type == WorksheetType.REPOS;
    }

    public boolean isGitRepo() {
        return this.location == WorksheetLocation.REPOS && this.type == WorksheetType.GIT_REPO;
    }

    public boolean isSystemDefine() {
        return isRoot() || isRepos() || isWorksheets() || isGitRepo();
    }

    /**
     * Rename. Only when the current path matches {@param from} will it be renamed as
     * {@param destinationPath} and return true. If it does not match, no renaming will be performed and
     * return false The {@param from} and {@param destinationPath} here need to satisfy
     * {@link WorksheetPathUtil#isRenameValid}, and the reason why this validation is not added here is
     * that it will not duplicate verify when renaming multiple times.
     * 
     * @param from the path need to rename
     * @param destinationPath path after renamed
     * @return is renamed
     */
    public boolean rename(Path from, Path destinationPath) {
        if (!this.isRenameMatch(from)) {
            return false;
        }
        // This is renaming the {@param from} itself
        if (from.levelNum.equals(this.levelNum)) {
            this.name = destinationPath.name;
            return true;
        }
        // This is renaming the sub items of {@param from}
        this.parentPathItems.set(from.levelNum - 1, destinationPath.name);
        return true;
    }

    /**
     * Matching rule: The current {@link Path} is either the same as {@param from} or a subset of
     * {@param from}
     * 
     * @param from
     * @return
     */
    public boolean isRenameMatch(Path from) {
        // current {@link Path} is same as {@param from}
        if (this.equals(from)) {
            return true;
        }
        // current {@link Path} is subset of {@param from}.
        // At this point, {@param from} needs to be directory type (with subsets)
        return from.isDirectory() && this.levelNum > from.levelNum
                && CollectionUtils.isEqualCollection(this.parentPathItems.subList(0, from.levelNum - 1),
                        from.parentPathItems.subList(0, from.levelNum - 1))
                && this.parentPathItems.get(from.levelNum - 1).equals(from.name);
    }

    /**
     * is {@link Path#name} contains {@param name}
     *
     * @param name
     * @return
     */
    public boolean isNameContains(String name) {
        return this.name.contains(name);
    }

    /**
     * Determine if the current path can be renamed
     * 
     * @return
     */
    public boolean canRename() {
        // Only subsets of /Worksheets/ and /Repos/RepoName/ can be renamed
        return this.location == WorksheetLocation.WORKSHEETS && this.levelNum > 1
                || this.location == WorksheetLocation.REPOS && this.levelNum > 2;
    }

    /**
     * Path sorted comparator, sorted by type+name(in the next level common parent).
     *
     * @return
     */
    public static Comparator<Path> getPathComparator() {
        return (o1, o2) -> {
            if (o1.equals(o2)) {
                return 0;
            }
            Path commonParentPath = WorksheetPathUtil.findCommonPath(
                    new HashSet<>(Arrays.asList(o1, o2)));
            int sameLevel = commonParentPath.levelNum;
            if (sameLevel == o1.levelNum) {
                return -1;
            } else if (sameLevel == o2.levelNum) {
                return 1;
            }
            return compareAtSameLevelWithSamePrevParent(o1.getPathAt(sameLevel),
                    o2.getPathAt(sameLevel));
        };
    }

    private static int compareAtSameLevelWithSamePrevParent(Path o1, Path o2) {
        if (!o1.levelNum.equals(o2.levelNum)) {
            throw new IllegalArgumentException("level num is not same,o1:" + o1 + ",o2:" + o2);
        }

        if (o1.type == o2.type) {
            return o1.name.compareTo(o2.name);
        }
        return Integer.compare(o1.type.getOrder(), o2.type.getOrder());
    }

    /**
     * sorted by levelNum
     * 
     * @return
     */
    public static Comparator<Path> getLevelNumComparator() {
        return Comparator.comparingInt(o -> o.levelNum);
    }

    public boolean isFile() {
        return this.type == WorksheetType.FILE;
    }

    public boolean isDirectory() {
        return this.type != WorksheetType.FILE;
    }

    public boolean isExceedNameLengthLimit() {
        return this.getName().length() > NAME_LENGTH_LIMIT;
    }

    public void canGetDownloadUrlCheck() {
        if (isDirectory()) {
            throw new IllegalArgumentException("can not get download url for directory,path" + this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Path path = (Path) o;
        return CollectionUtils.isEqualCollection(parentPathItems, path.parentPathItems)
                && Objects.equals(name, path.name)
                && type == path.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(parentPathItems, name, type);
    }

    @Override
    public String toString() {
        return getStandardPath();
    }
}
