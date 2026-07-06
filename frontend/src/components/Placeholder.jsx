function Placeholder({ title, description }) {
  return (
    <div className="flex h-full flex-col">
      <header className="border-b border-gray-200 bg-white px-6 py-4">
        <h1 className="text-lg font-semibold text-gray-800">{title}</h1>
        {description && <p className="mt-0.5 text-sm text-gray-500">{description}</p>}
      </header>
      <div className="flex flex-1 items-center justify-center">
        <div className="text-center">
          <div className="mx-auto mb-3 flex h-14 w-14 items-center justify-center rounded-full bg-gray-100">
            <svg className="h-7 w-7 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M5 13l4 4L19 7" />
            </svg>
          </div>
          <p className="text-sm text-gray-400">功能开发中</p>
        </div>
      </div>
    </div>
  )
}

export default Placeholder
