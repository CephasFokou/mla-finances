import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Icon } from '../../components/ui/Icon'
import ui from '../../components/ui/ui.module.scss'
import {
  useCategoriesAdminQuery,
  useCreateCategoryMutation,
  useToggleCategoryActiveMutation,
  useUpdateCategoryMutation,
} from '../../features/projects/hooks'
import type { Category, CategoryInput } from '../../features/projects/types'
import { ApiError } from '../../lib/apiClient'
import styles from './ProjectsListPage.module.scss'

/** Ligne éditable en place : évite une page dédiée pour un objet aussi simple qu'une catégorie. */
function CategoryRow({ category }: { category: Category }) {
  const { t } = useTranslation()
  const [isEditing, setIsEditing] = useState(false)
  const [name, setName] = useState(category.name)
  const [description, setDescription] = useState(category.description ?? '')
  const [error, setError] = useState<string | null>(null)

  const updateMutation = useUpdateCategoryMutation(category.id)
  const toggleMutation = useToggleCategoryActiveMutation(category.id)

  const startEditing = () => {
    setName(category.name)
    setDescription(category.description ?? '')
    setError(null)
    setIsEditing(true)
  }

  const save = async () => {
    setError(null)
    try {
      await updateMutation.mutateAsync({ name, description: description || undefined })
      setIsEditing(false)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('categories.form.genericError'))
    }
  }

  if (isEditing) {
    return (
      <tr>
        <td colSpan={4}>
          <div className={styles.inlineForm}>
            <label className={`${ui.field} ${styles.inlineField}`}>
              {t('categories.form.name')}
              <input value={name} onChange={(e) => setName(e.target.value)} />
            </label>
            <label className={`${ui.field} ${styles.inlineField}`}>
              {t('categories.form.description')}
              <input value={description} onChange={(e) => setDescription(e.target.value)} />
            </label>
            {error && <span className={ui.error}>{error}</span>}
            <div className={ui.actions}>
              <button
                type="button"
                className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
                onClick={() => setIsEditing(false)}
              >
                {t('categories.form.cancel')}
              </button>
              <button
                type="button"
                className={`${ui.btn} ${ui.btnPrimary} ${ui.btnSm}`}
                disabled={!name.trim() || updateMutation.isPending}
                onClick={() => void save()}
              >
                {t('categories.form.save')}
              </button>
            </div>
          </div>
        </td>
      </tr>
    )
  }

  return (
    <tr>
      <td className={ui.cellMain}>{category.name}</td>
      <td className={ui.cellSub}>{category.description || '—'}</td>
      <td>
        <span className={`${ui.pill} ${category.active ? ui.pillGreen : ui.pillGrey}`}>
          {category.active ? t('categories.active') : t('categories.inactive')}
        </span>
      </td>
      <td className={styles.rowActions}>
        <button
          type="button"
          className={ui.iconBtn}
          aria-label={t('categories.edit')}
          title={t('categories.edit')}
          onClick={startEditing}
        >
          <Icon name="edit" size={14} />
        </button>
        <button
          type="button"
          className={`${ui.btn} ${ui.btnSecondary} ${ui.btnSm}`}
          disabled={toggleMutation.isPending}
          onClick={() => void toggleMutation.mutateAsync(category.active)}
        >
          {category.active ? t('categories.deactivate') : t('categories.activate')}
        </button>
      </td>
    </tr>
  )
}

function NewCategoryForm({ onDone }: { onDone: () => void }) {
  const { t } = useTranslation()
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [error, setError] = useState<string | null>(null)
  const createMutation = useCreateCategoryMutation()

  const submit = async () => {
    setError(null)
    const input: CategoryInput = { name, description: description || undefined }
    try {
      await createMutation.mutateAsync(input)
      setName('')
      setDescription('')
      onDone()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('categories.form.genericError'))
    }
  }

  return (
    <div className={`${ui.card} ${styles.newCategoryForm}`}>
      <div className={ui.formGrid}>
        <label className={ui.field}>
          {t('categories.form.name')}
          <input value={name} onChange={(e) => setName(e.target.value)} />
        </label>
        <label className={ui.field}>
          {t('categories.form.description')}
          <input value={description} onChange={(e) => setDescription(e.target.value)} />
        </label>
      </div>
      {error && <span className={ui.error}>{error}</span>}
      <div className={ui.actions}>
        <button
          type="button"
          className={`${ui.btn} ${ui.btnPrimary} ${ui.btnSm}`}
          disabled={!name.trim() || createMutation.isPending}
          onClick={() => void submit()}
        >
          <Icon name="plus" strokeWidth={2.4} />
          {t('categories.form.submit')}
        </button>
      </div>
    </div>
  )
}

export function CategoriesPanel() {
  const { t } = useTranslation()
  const { data: categories, isLoading, isError } = useCategoriesAdminQuery()
  const [isCreating, setIsCreating] = useState(false)

  return (
    <div>
      <div className={`${ui.actions} ${styles.panelActions}`}>
        {!isCreating && (
          <button
            type="button"
            className={`${ui.btn} ${ui.btnPrimary} ${ui.btnSm}`}
            onClick={() => setIsCreating(true)}
          >
            <Icon name="plus" strokeWidth={2.4} />
            {t('categories.new')}
          </button>
        )}
      </div>

      {isCreating && <NewCategoryForm onDone={() => setIsCreating(false)} />}

      <div className={`${ui.card} ${ui.cardFlush}`}>
        {isLoading && (
          <div className={styles.skeletons}>
            <div className={ui.skel} />
            <div className={ui.skel} />
          </div>
        )}
        {isError && (
          <div className={ui.emptyState}>
            <div className={ui.emptyIcon}>
              <Icon name="alert" size={24} />
            </div>
            <h4>{t('categories.error')}</h4>
          </div>
        )}
        {!isLoading && !isError && categories?.length === 0 && (
          <div className={ui.emptyState}>
            <h4>{t('categories.empty')}</h4>
          </div>
        )}
        {!isLoading && !isError && !!categories?.length && (
          <div className={ui.tableWrap}>
            <table className={ui.table}>
              <thead>
                <tr>
                  <th>{t('categories.columns.name')}</th>
                  <th>{t('categories.columns.description')}</th>
                  <th>{t('categories.columns.status')}</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {categories.map((category) => (
                  <CategoryRow key={category.id} category={category} />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
