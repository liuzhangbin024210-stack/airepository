"""冒泡排序（Bubble Sort）示例实现。"""

from __future__ import annotations


def bubble_sort(arr: list[int | float]) -> list[int | float]:
    """
    对列表做升序冒泡排序，返回新列表，不修改原列表。
    时间复杂度 O(n^2)，空间 O(n)。
    """
    a = list(arr)
    n = len(a)
    for i in range(n - 1):
        swapped = False
        for j in range(n - 1 - i):
            if a[j] > a[j + 1]:
                a[j], a[j + 1] = a[j + 1], a[j]
                swapped = True
        if not swapped:
            break
    return a


def bubble_sort_inplace(arr: list[int | float]) -> None:
    """原地升序冒泡排序，修改传入的列表。"""
    n = len(arr)
    for i in range(n - 1):
        swapped = False
        for j in range(n - 1 - i):
            if arr[j] > arr[j + 1]:
                arr[j], arr[j + 1] = arr[j + 1], arr[j]
                swapped = True
        if not swapped:
            break


if __name__ == "__main__":
    data = [64, 34, 25, 12, 22, 11, 90]
    print("原列表:", data)
    print("新列表:", bubble_sort(data))
    print("原列表未改:", data)

    bubble_sort_inplace(data)
    print("原地排序后:", data)
