#!/usr/bin/env bash
set -euo pipefail

sdk_dir=$1
manifest="$(dirname "${BASH_SOURCE[0]}")/robolectric-sdks.sha256"
repositories=(
    https://maven-central.storage-download.googleapis.com/maven2
    https://repo.maven.apache.org/maven2
)

matches_checksum() {
    printf '%s  %s\n' "$1" "$2" | sha256sum --check --status
}

mkdir -p "$sdk_dir"
download=$(mktemp "$sdk_dir/.download.XXXXXX")
trap 'rm -f "$download"' EXIT

# Robolectric 4.15.1 uses API 28 and 34 for these tests and API 35 for app resources.
while read -r checksum jar; do
    target="$sdk_dir/$jar"
    if [[ -f "$target" ]] && matches_checksum "$checksum" "$target"; then
        continue
    fi

    version=${jar#android-all-instrumented-}
    version=${version%.jar}
    for repository in "${repositories[@]}"; do
        if curl --fail --silent --show-error --location --retry 2 --retry-max-time 90 \
            --connect-timeout 15 --max-time 150 --output "$download" \
            "$repository/org/robolectric/android-all-instrumented/$version/$jar" \
            && matches_checksum "$checksum" "$download"; then
            mv "$download" "$target"
            break
        fi
    done
    if [[ ! -f "$target" ]] || ! matches_checksum "$checksum" "$target"; then
        echo "Could not download and verify $jar" >&2
        exit 1
    fi
done < "$manifest"
