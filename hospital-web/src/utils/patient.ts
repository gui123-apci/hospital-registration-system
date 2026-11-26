export interface RelationOption {
  label: string
  value: number
}

export const relationOptions: RelationOption[] = [
  { label: '本人', value: 0 },
  { label: '父母', value: 1 },
  { label: '子女', value: 2 },
  { label: '其他', value: 3 }
]

export function relationLabel(code: number) {
  return relationOptions.find((option) => option.value === code)?.label ?? '未知关系'
}

export function maskIdCard(value?: string) {
  if (!value) return '未填写'
  if (value.length >= 18) return `${value.slice(0, 6)}********${value.slice(-4)}`
  if (value.length >= 8) {
    return `${value.slice(0, 3)}${'*'.repeat(value.length - 5)}${value.slice(-2)}`
  }
  return '*'.repeat(Math.max(8, value.length))
}
